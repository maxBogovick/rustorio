package com.webminer;

/**
 * A fetched page drawn to pixels — kept PNG-encoded rather than as pixels, and that is the whole
 * reason this type exists instead of an image field.
 *
 * <p>A page rendered at readable size is 720 by up to a couple of thousand pixels; as an {@code
 * int[]} that is four to six megabytes, held per monitored cell, whether or not the player ever
 * opens it. A factory with twenty miners would carry a hundred megabytes of screenshots nobody
 * asked to see. The same render as PNG is a hundred kilobytes or so — page renders are mostly flat
 * white, which is the case PNG compresses best — and decoding it back costs a few milliseconds at
 * the moment a window actually opens.
 *
 * @param png the encoded image
 * @param width pixel width of the encoded image, so a viewer can lay itself out before decoding
 * @param height pixel height, same reason
 */
record RenderedPage(byte[] png, int width, int height) {
}
