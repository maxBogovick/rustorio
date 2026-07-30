package com.rustorio.domain;

/**
 * Which silhouette an item's cargo icon draws as — color alone is an accessibility failure (two
 * items can render as nearly the same gray), shape is the second, independent channel.
 */
public enum ItemShape {
    CIRCLE,
    SQUARE,
    TRIANGLE
}
