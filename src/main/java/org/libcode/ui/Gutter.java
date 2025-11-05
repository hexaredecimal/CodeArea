package org.libcode.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.libcode.lines.LineNumberMode;
import org.libcode.colors.Colors;

/**
 *
 * @author hexaredecimal
 */
public class Gutter extends JComponent {

	private final JTextPane textPane;
	private final Font gutterFont = new Font("Monospaced", Font.PLAIN, 14);
	private final Set<Integer> breakpoints = new HashSet<>();
	private final Set<Integer> bookmarks = new HashSet<>();
	private LineNumberMode mode = LineNumberMode.ABSOLUTE;

	public Gutter(JTextPane textPane, JScrollPane scrollPane) {
		this.textPane = textPane;

		scrollPane.getVerticalScrollBar().getModel().addChangeListener(e -> repaint());

		this.textPane.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				updateWidth();
				repaint();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				updateWidth();
				repaint();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				updateWidth();
				repaint();
			}
		});

		this.textPane.addCaretListener(e -> {
			updateWidth();
			repaint();
		});

		setPreferredSize(new Dimension(40, 0));
		setupContextMenu();

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (textPane instanceof CodePane ctp) {
					int line = getLineAtClick(e.getY());
					if (line >= 0 && ctp.isFoldStartLine(line)) {
						int x = e.getX();
						if (x >= 10 && x <= 28) {
							ctp.toggleFoldAtLine(line);
							repaint();
							textPane.repaint();
						}
					}
				}
			}

			private int getLineAtClick(int y) {
				FontMetrics fm = textPane.getFontMetrics(textPane.getFont());
				int lineHeight = fm.getHeight();
				JViewport viewport = (JViewport) getParent();
				if (viewport == null) {
					return -1;
				}
				int scrollOffset = viewport.getViewPosition().y;
				return (y + scrollOffset) / lineHeight;
			}
		});
	}

	public void setMode(LineNumberMode mode) {
		this.mode = mode;
		repaint();
	}

	private void setupContextMenu() {
		JPopupMenu contextMenu = new JPopupMenu();
		JMenuItem toggleBreakpointItem = new JMenuItem("Toggle Breakpoint");
		toggleBreakpointItem.addActionListener(e -> {
			int line = getLineAtMouse();
			if (line >= 0) {
				if (breakpoints.contains(line)) {
					breakpoints.remove(line);
				} else {
					breakpoints.add(line);
				}
				repaint();
			}
		});
		contextMenu.add(toggleBreakpointItem);

		JMenuItem toggleBookmarkItem = new JMenuItem("Toggle Bookmark");
		toggleBookmarkItem.addActionListener(e -> {
			int line = getLineAtMouse();
			if (line >= 0) {
				if (bookmarks.contains(line)) {
					bookmarks.remove(line);
				} else {
					bookmarks.add(line);
				}
				repaint();
			}
		});
		contextMenu.add(toggleBookmarkItem);

		// Folding actions
		JMenuItem foldBlockItem = new JMenuItem("Fold Block");
		foldBlockItem.addActionListener(e -> {
			int line = getLineAtMouse();
			if (line >= 0 && textPane instanceof CodePane ctp) {
				ctp.toggleFoldAtLine(line);
				repaint();
			}
		});
		contextMenu.add(foldBlockItem);

		JMenuItem unfoldBlockItem = new JMenuItem("Unfold Block");
		unfoldBlockItem.addActionListener(e -> {
			int line = getLineAtMouse();
			if (line >= 0 && textPane instanceof CodePane ctp) {
				// toggle only if currently collapsed at this line
				if (ctp.isFoldStartLine(line) && ctp.isFoldCollapsed(line)) {
					ctp.toggleFoldAtLine(line);
					repaint();
				}
			}
		});
		contextMenu.add(unfoldBlockItem);
		setComponentPopupMenu(contextMenu);
	}

	@Override
	public Dimension getPreferredSize() {
		FontMetrics fm = textPane.getFontMetrics(gutterFont);
		int lineHeight = fm.getHeight();

		Rectangle visibleRect = textPane.getVisibleRect();
		int startLine = visibleRect.y / lineHeight;
		int endLine = Math.min((visibleRect.y + visibleRect.height) / lineHeight,
						textPane.getDocument().getDefaultRootElement().getElementCount() - 1);

		int caretLine = textPane.getDocument().getDefaultRootElement().getElementIndex(textPane.getCaretPosition());

		// Find the largest number in the visible range
		int maxNumber = 1; // at least 1 digit
		for (int i = startLine; i <= endLine; i++) {
			int number = (mode == LineNumberMode.ABSOLUTE) ? i + 1 : Math.abs(i - caretLine);
			if (number > maxNumber) {
				maxNumber = number;
			}
		}

		int digits = String.valueOf(maxNumber).length();
		int baseWidth = fm.charWidth('0'); // width for a single digit
		int width = baseWidth * digits + 10; // padding

		return new Dimension(width + 30, textPane.getHeight());
	}

	private void updateWidth() {
		setPreferredSize(getPreferredSize());
		revalidate(); // important so the layout updates
	}

	private int getLineAtMouse() {
		Point mousePos = getMousePosition();
		if (mousePos == null) {
			return -1;
		}
		int lineHeight = textPane.getFontMetrics(textPane.getFont()).getHeight();
		int scrollOffset = ((JViewport) getParent()).getViewPosition().y; // adjust for scroll
		return (mousePos.y + scrollOffset) / lineHeight;
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		g.setColor(Colors.getGutterBackgroundColor());
		g.fillRect(0, 0, getWidth(), getHeight());

		FontMetrics fm = textPane.getFontMetrics(textPane.getFont());
		int lineHeight = fm.getHeight();

		int lineCount = textPane.getDocument().getDefaultRootElement().getElementCount();
		int caretPos = textPane.getCaretPosition();
		int currentLine = textPane.getDocument().getDefaultRootElement().getElementIndex(caretPos);

		g.setFont(gutterFont);

		if (textPane instanceof CodePane ctp) {
			// Paint visible lines continuously without gaps
			int visualY = 0; // Track visual Y position (no gaps)
			for (int i = 0; i < lineCount; i++) {
				if (!ctp.isLineVisible(i)) {
					continue; // Skip collapsed lines
				}
				int lineY = visualY * lineHeight + fm.getAscent();
				int lineYTop = visualY * lineHeight;

				// Highlight current line
				if (i == currentLine) {
					g.setColor(Colors.getCurrentLineColor());
					g.fillRect(0, lineYTop, getWidth(), lineHeight);
				}

				// Line numbers - use displayed line number (skipping collapsed lines)
				int displayNum = ctp.getDisplayLineNumber(i);
				String lineNumber = (mode == LineNumberMode.ABSOLUTE) ? String.valueOf(displayNum)
								: (i == currentLine ? "0" : String.valueOf(Math.abs(displayNum - ctp.getDisplayLineNumber(currentLine))));
				int stringWidth = fm.stringWidth(lineNumber);
				g.setColor(Colors.getGutterForegroundColor());
				g.drawString(lineNumber, getWidth() - stringWidth - 10, lineY);

				// Breakpoints (use original line number)
				if (breakpoints.contains(i + 1)) {
					g.setColor(Colors.getBreakPointColor());
					g.fillOval(5, lineYTop + lineHeight / 2 - 4, 8, 8);
				}

				// Bookmarks (use original line number)
				if (bookmarks.contains(i + 1)) {
					g.setColor(Colors.getBookmarkColor());
					g.fillRect(5, lineYTop + lineHeight / 2 - 4, 8, 8);
				}

				// Folding markers (triangles)
				if (ctp.isFoldStartLine(i)) {
					boolean collapsed = ctp.isFoldCollapsed(i);
					int cx = 18; // marker offset
					int cy = lineYTop + lineHeight / 2;
					Polygon tri = new Polygon();
					if (collapsed) {
						// right-pointing
						tri.addPoint(cx - 4, cy - 5);
						tri.addPoint(cx - 4, cy + 5);
						tri.addPoint(cx + 2, cy);
					} else {
						// down-pointing
						tri.addPoint(cx - 5, cy - 2);
						tri.addPoint(cx + 5, cy - 2);
						tri.addPoint(cx, cy + 4);
					}
					g.setColor(Colors.getFoldToggleColor());
					g.fillPolygon(tri);
				}

				visualY++; // Increment visual Y position for next visible line
			}
		} else {
			// Fallback for non-CustomTextPane (shouldn't happen)
			for (int i = 0; i < lineCount; i++) {
				int lineY = i * lineHeight + fm.getAscent();
				if (i == currentLine) {
					g.setColor(new Color(210, 210, 220));
					g.fillRect(0, i * lineHeight, getWidth(), lineHeight);
				}
				String lineNumber = (mode == LineNumberMode.ABSOLUTE) ? String.valueOf(i + 1)
								: (i == currentLine ? "0" : String.valueOf(Math.abs(i - currentLine)));
				int stringWidth = fm.stringWidth(lineNumber);
				g.setColor(Colors.getGutterForegroundColor());
				g.drawString(lineNumber, getWidth() - stringWidth - 10, lineY);
			}
		}
	}

}
