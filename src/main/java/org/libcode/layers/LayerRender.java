package org.libcode.layers;

import java.awt.Graphics2D;

/**
 *
 * @author hexaredecimal
 */

@FunctionalInterface
public interface LayerRender {
  void draw(int width, int height, Graphics2D g2d);
}
