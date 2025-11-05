package org.libcode.layers;

/**
 *
 * @author hexaredecimal
 */

public class Layer {
  public int layer;
  public LayerRender render;
  public boolean active;

  public Layer(int layer, LayerRender render) {
    this.layer = layer;
    this.render = render;
    this.active = true;
  }
}
