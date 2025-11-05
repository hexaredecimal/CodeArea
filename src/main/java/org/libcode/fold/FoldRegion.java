package org.libcode.fold;

import javax.swing.text.Position;

/**
 *
 * @author hexaredecimal
 */
public class FoldRegion {
		public final Position startPos; // start line (visible), region hides lines after this up to end
		public final Position endPos;   // end line inclusive
		public boolean collapsed;

		public FoldRegion(Position startPos, Position endPos, boolean collapsed) {
			this.startPos = startPos;
			this.endPos = endPos;
			this.collapsed = collapsed;
		}

		public int getStartOffset() {
			return startPos.getOffset();
		}

		public int getEndOffset() {
			return endPos.getOffset();
		}
}
