package com.webminer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import org.jspecify.annotations.Nullable;

/**
 * Draws a fetched page with the HTML viewer the JDK already ships — {@link JEditorPane} plus
 * {@link HTMLEditorKit} — into a {@link RenderedPage}.
 *
 * <p>No new dependency and no browser engine: this is Swing's own renderer, run headless, painting
 * into a {@link BufferedImage} with no window anywhere. Owner's decision (08.08.2026) after seeing
 * what it produces, which is worth stating plainly so nobody is surprised later: it understands
 * HTML 3.2 and a subset of CSS 1. Headings, paragraphs, lists, tables, links, bold and images all
 * come out right. Anything a page written this decade relies on — grid, flexbox, custom
 * properties, {@code clamp()} — is ignored, so a modern site renders as a plain unstyled document
 * rather than as it looks in a browser. On a measured 235 KB page the whole thing takes about a
 * third of a second.
 *
 * <p><b>Runs on the fetch thread, never the tick thread.</b> {@link HttpFetchExecutor} calls this
 * from the same background thread the HTTP request came back on: it is far too slow for a tick and
 * — the harder constraint — the full document only exists there, before {@code MAX_BODY_LENGTH}
 * discards all but the first few kilobytes.
 *
 * <p><b>Never throws.</b> A page that will not render is a page shown as text, which is what the
 * monitor does anyway; it is not a reason for a fetch to fail. Every failure path returns {@code
 * null}, {@link Throwable} included, because Swing's HTML parser is being handed arbitrary bytes
 * off the public internet and "arbitrary" includes inputs nobody has thought about.
 */
final class PageRenderer {

    /**
     * Set before any AWT class loads. The game's own window is GLFW's, and this process must never
     * try to open an AWT one beside it — on macOS both want the first thread and the loser tends to
     * take the process with it. Headless mode makes that structurally impossible instead of merely
     * unlikely: painting into a {@link BufferedImage} needs no display and no window server.
     */
    static {
        System.setProperty("java.awt.headless", "true");
    }

    /** Rendering width. Wide enough that a page's own layout has room to be itself, narrow enough that the result is legible when a viewer scales it down. */
    private static final int WIDTH = 720;
    /** Tallest render kept. A page longer than this is cut off at the bottom — a viewer that scrolls forever through an unbounded screenshot is not what a monitor is for. */
    private static final int MAX_HEIGHT = 2400;
    /** Shortest sensible render, so a page that lays out to nothing still produces a valid image rather than a zero-height one. */
    private static final int MIN_HEIGHT = 40;
    /**
     * How long to let images arrive. {@link HTMLEditorKit} loads {@code <img>} asynchronously and
     * has no completion callback worth waiting on, so this polls the laid-out height instead: two
     * consecutive identical measurements mean nothing else has landed. Bounded, because a page
     * pulling a hundred images from a slow CDN must not hold a fetch thread indefinitely.
     */
    private static final long IMAGE_WAIT_STEP_MS = 120;
    private static final int IMAGE_WAIT_STEPS = 16; // ~2 s worst case

    private PageRenderer() {
    }

    /**
     * {@code html} drawn as a page, or {@code null} if it could not be drawn at all.
     *
     * @param baseUrl the URL it was fetched from, so relative {@code <img>} and stylesheet
     *                references resolve. Images ARE loaded (owner's decision): that means this
     *                process makes requests to whatever hosts the page names — a CDN, an analytics
     *                pixel — and not only to the URL the player typed. Nothing here follows links
     *                or runs scripts; {@link HTMLEditorKit} has no scripting engine at all.
     */
    static @Nullable RenderedPage render(String html, String baseUrl) {
        try {
            JEditorPane pane = new JEditorPane();
            pane.setEditable(false);
            HTMLEditorKit kit = new HTMLEditorKit();
            pane.setEditorKit(kit);
            pane.setBackground(Color.WHITE);
            HTMLDocument document = (HTMLDocument) kit.createDefaultDocument();
            // Relative URLs resolve against the page's own address, exactly as they would in a
            // browser; without a base, every <img src="/a.png"> is a broken-image icon.
            document.setBase(baseOf(baseUrl));
            pane.setDocument(document);
            pane.setText(html);
            int height = layOut(pane);
            return new RenderedPage(encode(paint(pane, height)), WIDTH, height);
        } catch (Throwable failure) {
            return null; // see the class javadoc: a page that won't draw is not a failed fetch
        }
    }

    /** Lays the document out at {@link #WIDTH} and waits for the height to stop changing — see {@link #IMAGE_WAIT_STEP_MS}. */
    private static int layOut(JEditorPane pane) throws InterruptedException {
        pane.setSize(new Dimension(WIDTH, Short.MAX_VALUE));
        int previous = -1;
        for (int step = 0; step < IMAGE_WAIT_STEPS; step++) {
            int measured = pane.getPreferredSize().height;
            if (measured == previous && measured > 0) {
                break;
            }
            previous = measured;
            Thread.sleep(IMAGE_WAIT_STEP_MS);
        }
        int height = Math.max(pane.getPreferredSize().height, previous);
        return Math.clamp(height, MIN_HEIGHT, MAX_HEIGHT);
    }

    private static BufferedImage paint(JEditorPane pane, int height) {
        pane.setSize(WIDTH, height);
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, WIDTH, height); // a page's own background is transparent where it sets none
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        pane.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static byte[] encode(BufferedImage image) throws java.io.IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 * 1024);
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    /** The fetched URL as a base, or {@code null} when it isn't one — a page with an unusable base still renders, just without its images. */
    private static @Nullable URL baseOf(String url) {
        try {
            return URI.create(url).toURL();
        } catch (RuntimeException | java.net.MalformedURLException e) {
            return null;
        }
    }

    /** Decodes what {@link #render} produced back into pixels, at the moment a viewer needs them — see {@link RenderedPage} for why they aren't kept this way. */
    static int @Nullable [] decode(RenderedPage page) {
        try {
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(page.png()));
            if (image == null) {
                return null;
            }
            return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        } catch (Throwable failure) {
            return null;
        }
    }
}
