package org.libcode.fold.view;

import java.awt.Graphics;
import java.awt.Shape;
import javax.swing.text.Element;
import javax.swing.text.ParagraphView;
import javax.swing.text.View;
import org.libcode.ui.CodePane;

/**
 *
 * @author hexaredecimal
 */
public class FoldingParagraphView extends ParagraphView {

	private final CodePane pane;

	public FoldingParagraphView(Element elem, CodePane pane) {
		super(elem);
		this.pane = pane;
	}

	@Override
	public float getPreferredSpan(int axis) {
		if (axis == View.Y_AXIS && pane.isParagraphHidden(getStartOffset())) {
			return 0f;
		}
		return super.getPreferredSpan(axis);
	}

	@Override
	public float getMinimumSpan(int axis) {
		if (axis == View.Y_AXIS && pane.isParagraphHidden(getStartOffset())) {
			return 0f;
		}
		return super.getMinimumSpan(axis);
	}

	@Override
	public float getMaximumSpan(int axis) {
		if (axis == View.Y_AXIS && pane.isParagraphHidden(getStartOffset())) {
			return 0f;
		}
		return super.getMaximumSpan(axis);
	}

	@Override
	public void paint(Graphics g, Shape a) {
		if (pane.isParagraphHidden(getStartOffset())) {
			return;
		}
		super.paint(g, a);
	}
}
