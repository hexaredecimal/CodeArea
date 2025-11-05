package org.libcode.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.View;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.text.Utilities;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import org.libcode.colors.Colors;
import org.libcode.intelisense.IntellisenseItem;
import org.libcode.fold.FoldRegion;
import org.libcode.fold.FoldingEditorKit;
import org.libcode.fold.view.FoldingParagraphView;
import org.libcode.layers.Layer;
import org.libcode.layers.LayerIndex;
import org.libcode.layers.LayerRender;

/**
 *
 * @author hexaredecimal
 */
public final class CodePane extends JTextPane {

	private final Set<Integer> errorLines = new HashSet<>();
	private final Map<Integer, String> errorMessages = new HashMap<>();
	private final HashMap<List<String>, Style> highlightTable = new HashMap<>();

	private final List<IntellisenseItem> docCompletions = new ArrayList<>();

	private JPopupMenu completionMenu;
	private int selectedSuggestionIndex = 0;
	private final Font editorFont = new Font("Monospaced", Font.PLAIN, 14);
	private int currentLine = 0;
	private UndoManager undoManager = new UndoManager();

	private List<Layer> layers;
	private boolean autoFoldingEnabled = true;
	private final java.util.Map<String, String> foldTriggerPairs = new java.util.LinkedHashMap<>();


	public CodePane() {
		setFont(editorFont);
		setOpaque(false);
		layers = new ArrayList<>();
		setEditorKit(new FoldingEditorKit(this));
		setupCompletionMenu();
		setupContextMenu();
		if (autoFoldingEnabled) {
			SwingUtilities.invokeLater(this::rebuildFoldRegions);
		}

		setBackground(Colors.getBackgroundColor());
		setForeground(Colors.getForegroundColor());
		var bg = getBackground();
		
		getDocument().addUndoableEditListener(e -> {
			UndoableEdit edit = e.getEdit();
			if (edit instanceof AbstractDocument.DefaultDocumentEvent docEvent) {
				if (docEvent.getType() == DocumentEvent.EventType.INSERT
								|| docEvent.getType() == DocumentEvent.EventType.REMOVE) {
					undoManager.addEdit(edit);
					if (autoFoldingEnabled) {
						// Rebuild folds after edits (debounced to EDT tail)
						SwingUtilities.invokeLater(this::rebuildFoldRegions);
					}
				}

			}
		});

		addCaretListener(e -> {
			try {
				int caretPos = e.getDot();
				int line = getDocument().getDefaultRootElement().getElementIndex(caretPos);
				if (line != currentLine) {
					currentLine = line;
					repaint();
				}
			} catch (Exception ex) {
				// ignore
			}
		});

		var ed = this;

		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_SPACE) {
					showDocCompletion();
					e.consume();
				} else if (completionMenu.isVisible()) {
					handleCompletionNavigation(e);
				} else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					try {
						int caretPos = getCaretPosition();
						int lineStart = Utilities.getRowStart(CodePane.this, caretPos);
						String lineText = getText(lineStart, caretPos - lineStart);

						// Match leading whitespace
						java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*").matcher(lineText);
						String indent = "";
						if (m.find()) {
							indent = m.group();
						}

						// Insert newline + indent
						getDocument().insertString(caretPos, "\n" + indent, null);

						// Prevent default newline insertion
						e.consume();
					} catch (BadLocationException ex) {
					}
				}

			}

			@Override
			public void keyReleased(KeyEvent e) {
				if (e.isControlDown() || e.getKeyCode() == KeyEvent.VK_SPACE) {
					return;
				}

				if (Character.isLetterOrDigit(e.getKeyChar())) {
					showWordCompletion();
				} else {
					completionMenu.setVisible(false);
				}
				highlightSyntax();
			}

		});

		final FontMetrics fontMetrics = getFontMetrics(getFont());
		final int lineHeight = fontMetrics.getHeight();

		addLayer(LayerIndex.LINE_SELECT_LAYER, (w, h, g2) -> {
			int y = -1;

			try {
				y = modelToView(getSelectionStart()).y;
			} catch (BadLocationException e) {
			}

			if (y != -1) {

				g2.setColor(Colors.getCurrentLineColor());
				g2.fillRect(0, y - 2, w, lineHeight);
			}
		});

		addLayer(LayerIndex.GRIDY_LAYER, (w, h, g2) -> {
			g2.setColor(Colors.getGridColor());
			int gridSize = fontMetrics.getHeight();

			if (gridSize > 0) {
				for (int x = 0; x < w; x += gridSize) {
					g2.drawLine(x, 0, x, h);
				}
			}
		});

		addLayer(LayerIndex.GRIDX_LAYER, (w, h, g2) -> {
			g2.setColor(Colors.getGridColor());
			int gridSize = fontMetrics.getHeight();

			if (gridSize > 0) {
				for (int y_ = 0; y_ < h; y_ += gridSize) {
					g2.drawLine(0, y_, w, y_);
				}
			}
		});

		// folded inline indicator layer: draw " {...}" at end of the start line when collapsed
		addLayer(LayerIndex.TOP_FREE_LAYER--, (w, h, g2) -> {
			g2.setFont(getFont());
			final FontMetrics fm2 = getFontMetrics(getFont());
			for (FoldRegion fr : foldRegions) {
				if (!fr.collapsed) {
					continue;
				}
				int startLine = getLineFromOffset(fr.getStartOffset());
				try {
					Element rootEl = getDocument().getDefaultRootElement();
					Element lineEl = rootEl.getElement(startLine);
					if (lineEl == null) {
						continue;
					}
					int lineStart = lineEl.getStartOffset();
					int lineEnd = lineEl.getEndOffset();
					Rectangle rStart = modelToView(lineStart);
					Rectangle rEnd = modelToView(Math.max(lineStart, lineEnd - 1));
					if (rStart == null || rEnd == null) {
						continue;
					}
					// String label = " {...}";
					// int x = rEnd.x + 4;
					// int y = rEnd.y + fm2.getAscent();
					// g2.setColor(new Color(70, 70, 90));
					// g2.drawString(label, x, y);
				} catch (BadLocationException ignored) {
				}
			}
		});

		addLayer(LayerIndex.TOP_FREE_LAYER + 1, (w, h, g2) -> {
			int lineCount = ed.getDocument().getDefaultRootElement().getElementCount();
			for (int line : errorLines) {
				if (line < 0 || line >= lineCount) {
					continue;
				}
				try {
					Rectangle r = modelToView(getDocument().getDefaultRootElement().getElement(line).getStartOffset());
					if (r == null) {
						continue;
					}

					// Gradient background: left (white) → right (light red)
					GradientPaint gp = new GradientPaint(
									0, r.y, Colors.getErrorLeftGradientColor(),
									getWidth(), r.y, Colors.getErrorRightGradientColor()
					);
					g2.setPaint(gp);
					g2.fillRect(0, r.y - 2, w, lineHeight);

					// Draw error message (right side, bright red)
					String message = errorMessages.get(line);
					if (message != null && !message.isEmpty()) {
						g2.setColor(Colors.getErrorTextColor());
						int textWidth = fontMetrics.stringWidth(message);
						g2.drawString(message, getWidth() - textWidth - 10, r.y + fontMetrics.getAscent() - 2);
					}
				} catch (BadLocationException ignored) {
				}
			}
		});

	}

	public void addHighlightedWords(List<String> words, Color color, boolean  bold, boolean italic) {
		StyledDocument doc = getStyledDocument();
		Style defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
		var style = doc.addStyle("Highlight_" + words.size() + "_" + highlightTable.size(), defaultStyle);
		StyleConstants.setForeground(style, color);
		StyleConstants.setBold(style, bold);
		StyleConstants.setItalic(style, italic);
		highlightTable.put(words, style);
	}

	public void addCompletions(List<IntellisenseItem> completions) {
		docCompletions.addAll(completions);
	}

	public void addCompletion(IntellisenseItem completion) {
		docCompletions.add(completion);
	}

	public int getLine() {
		int caretPos = getCaretPosition();
		int line = getDocument().getDefaultRootElement().getElementIndex(caretPos);
		return line;
	}
	
	public void addLayer(int layer, LayerRender render) {
		layers.add(new Layer(layer, render));
		layers.sort((l1, l2) -> Integer.compare(l1.layer, l2.layer));
	}

	public Layer getLayer(int index) {
		for (var layer : layers) {
			if (layer.layer == index) {
				return layer;
			}
		}
		return null;
	}

	public void hideLayer(int index) {
		var layer = getLayer(index);
		if (layer == null) {
			return;
		}
		layer.active = false;
	}

	public void showLayer(int index) {
		var layer = getLayer(index);
		if (layer == null) {
			return;
		}
		layer.active = true;
	}

	public Layer removeLayer(int index) {
		for (int i = 0; i < layers.size(); ++i) {
			var layer = layers.get(i);
			if (layer.layer == index) {
				layers.remove(i);
				return layer;
			}
		}
		return null;
	}

	private void setupContextMenu() {
		JPopupMenu contextMenu = new JPopupMenu();
		setComponentPopupMenu(contextMenu);
	}

	public boolean canUndo() {
		return undoManager.canUndo();
	}

	public boolean canRedo() {
		return undoManager.canRedo();
	}

	public void undo() {
		if (undoManager.canUndo()) {
			undoManager.undo();
		}
	}

	public void redo() {
		if (undoManager.canRedo()) {
			undoManager.redo();
		}
	}

	private void handleCompletionNavigation(KeyEvent e) {
		int suggestionCount = completionMenu.getComponentCount();
		if (suggestionCount == 0) {
			return;
		}

		switch (e.getKeyCode()) {
			case KeyEvent.VK_DOWN:
				selectedSuggestionIndex = (selectedSuggestionIndex + 1) % suggestionCount;
				updateSuggestionHighlight();
				e.consume();
				break;
			case KeyEvent.VK_UP:
				selectedSuggestionIndex = (selectedSuggestionIndex - 1 + suggestionCount) % suggestionCount;
				updateSuggestionHighlight();
				e.consume();
				break;
			case KeyEvent.VK_ENTER:
				((JMenuItem) completionMenu.getComponent(selectedSuggestionIndex)).doClick();
				e.consume();
				break;
			case KeyEvent.VK_ESCAPE:
				completionMenu.setVisible(false);
				e.consume();
				break;
		}
	}

	public void markErrorLine(int line, String message) {
		errorLines.add(line);
		if (message != null && !message.isEmpty()) {
			errorMessages.put(line, message);
		}
		repaint();
	}

	public void unmarkErrorLine(int line) {
		if (errorLines.contains(line)) {
			errorLines.remove(line);
		}
		errorMessages.remove(line);
		repaint();
	}

	public void clearErrorLines() {
		errorLines.clear();
		errorMessages.clear();
		repaint();
	}

	private void updateSuggestionHighlight() {
		for (int i = 0; i < completionMenu.getComponentCount(); i++) {
			JMenuItem item = (JMenuItem) completionMenu.getComponent(i);
			item.setBackground(i == selectedSuggestionIndex ? new Color(210, 230, 255) : null);
		}
	}

	private List<String> getAllWords(String text) {
		List<String> words = new ArrayList<>();
		// \b ensures word boundaries; \w+ matches [A-Za-z0-9_]+ only
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b\\w+\\b").matcher(text);
		while (matcher.find()) {
			words.add(matcher.group());
		}
		return words;
	}


	@Override
	protected void paintComponent(Graphics g) {
		setBackground(Colors.getBackgroundColor());
		setForeground(Colors.getForegroundColor());
		g.setColor(Colors.getBackgroundColor());
		g.fillRect(0, 0, getWidth(), getHeight());
		Graphics2D g2 = (Graphics2D) g.create();
		for (var layer : layers) {
			if (layer.active) {
				layer.render.draw(getWidth(), getHeight(), g2);
			}
		}
		g.setColor(Colors.getForegroundColor());
		super.paintComponent(g);
	}

	private void highlightSyntax() {
		StyledDocument doc = getStyledDocument();
		String text;
		try {
			text = doc.getText(0, doc.getLength());
		} catch (BadLocationException e) {
			return;
		}

		Style defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
		doc.setCharacterAttributes(0, text.length(), defaultStyle, true);

		for (var kv: highlightTable.entrySet()) {
			for (String keyword : kv.getKey()) {
				int pos = 0;
				while ((pos = text.indexOf(keyword, pos)) >= 0) {
					if (isWholeWord(text, pos, keyword.length())) {
						doc.setCharacterAttributes(pos, keyword.length(), kv.getValue(), true);
					}
					pos += keyword.length();
				}
			}
		}
	}

	private boolean isWholeWord(String text, int start, int length) {
		char before = (start == 0) ? ' ' : text.charAt(start - 1);
		char after = (start + length >= text.length()) ? ' ' : text.charAt(start + length);
		return !Character.isLetterOrDigit(before) && !Character.isLetterOrDigit(after);
	}

	private void setupCompletionMenu() {
		completionMenu = new JPopupMenu();
		completionMenu.setFocusable(false);
	}

	private String getCurrentWord(int caretPosition) throws BadLocationException {
		String text = getText(0, caretPosition);
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b\\w+$").matcher(text);
		if (matcher.find()) {
			return matcher.group();
		}
		return "";
	}

	private void showWordCompletion() {
		try {
			int caretPosition = getCaretPosition();
			String text = getText(0, caretPosition);
			java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b\\w+$").matcher(text);
			int wordStart = caretPosition;
			String currentWord = "";
			if (matcher.find()) {
				currentWord = matcher.group();
				wordStart = matcher.start();
			}

			List<String> suggestions = new ArrayList<>();
			final String cw = currentWord;
			for (var keywords : highlightTable.keySet()) {
				suggestions.addAll(keywords.stream().filter(k -> k.startsWith(cw)).collect(Collectors.toList()));
			}
			suggestions.addAll(
							getAllWords(getText()).stream()
											.filter(w -> w.startsWith(cw) && !w.equals(cw))
											.distinct()
											.collect(Collectors.toList())
			);

			if (!suggestions.isEmpty()) {
				setupContextMenu();
				completionMenu.removeAll();
				final int finalWordStart = wordStart;
				final int finalCaretPosition = caretPosition;
				for (String suggestion : suggestions) {
					JMenuItem item = new JMenuItem(suggestion);
					item.addActionListener(ae -> {
						try {
							getDocument().remove(finalWordStart, finalCaretPosition - finalWordStart);
							getDocument().insertString(finalWordStart, suggestion, null);
							setCaretPosition(finalWordStart + suggestion.length());
						} catch (BadLocationException ex) {
							ex.printStackTrace();
						}
						completionMenu.setVisible(false);
					});
					completionMenu.add(item);
				}
				Point p = getUI().modelToView(this, caretPosition).getLocation();
				completionMenu.show(this, p.x, p.y + getFontMetrics(getFont()).getHeight());
				selectedSuggestionIndex = 0;
				updateSuggestionHighlight();
			} else {
				completionMenu.setVisible(false);
			}
		} catch (BadLocationException ex) {
			ex.printStackTrace();
		}
	}

	private JPanel getCompletionPanel(IntellisenseItem suggestion) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		JLabel nameLabel = new JLabel("<html><b>" + suggestion.getName() + "</b></html>");
		JLabel signatureLabel = new JLabel(suggestion.getSignature());
		signatureLabel.setForeground(Color.GRAY);
		JTextArea docArea = new JTextArea(suggestion.getDocumentation());
		docArea.setWrapStyleWord(true);
		docArea.setLineWrap(true);
		docArea.setEditable(false);
		docArea.setBackground(getBackground());

		panel.add(nameLabel, BorderLayout.NORTH);
		panel.add(signatureLabel, BorderLayout.CENTER);
		panel.add(docArea, BorderLayout.SOUTH);
		return panel;
	}

	private void showDocCompletion() {
		try {
			int caretPosition = getCaretPosition();
			String text = getText();

			int wordStart = caretPosition;
			while (wordStart > 0) {
				char c = text.charAt(wordStart - 1);
				if (!Character.isLetterOrDigit(c) && c != '_') {
					break; // stop at non-identifier
				}
				wordStart--;
			}

			int wordEnd = caretPosition;
			while (wordEnd < text.length()) {
				char c = text.charAt(wordEnd);
				if (!Character.isLetterOrDigit(c) && c != '_') {
					break;
				}
				wordEnd++;
			}

			if (wordStart >= wordEnd) {
				completionMenu.setVisible(false);
				return;
			}

			String currentWord = text.substring(wordStart, wordEnd);
			List<IntellisenseItem> _suggestions = docCompletions.stream()
							.filter(c -> c.getName().startsWith(currentWord))
							.sorted((c1, c2) -> {
								if (c1.getName().equals(currentWord)) {
									return -1;
								}
								if (c2.getName().equals(currentWord)) {
									return 1;
								}
								return c1.getName().compareTo(c2.getName());
							})
							.collect(Collectors.toList());

			List<IntellisenseItem> suggestions = new ArrayList<>(_suggestions);
			for (var suggestion : suggestions) {
				if (currentWord.compareTo(suggestion.getName()) == 0) {
					suggestions.clear();
					suggestions.add(suggestion);
					break;
				}
			}

			if (!suggestions.isEmpty()) {
				setupCompletionMenu();
				completionMenu.removeAll();

				if (suggestions.size() == 1 && !currentWord.equals(suggestions.getFirst().getName())) {
					var suggestion = suggestions.getFirst();
					var menuItem = new JMenuItem(suggestion.getName());
					menuItem.addActionListener(action -> {
						try {
							setupCompletionMenu();
							completionMenu.removeAll();
							completionMenu.setLayout(new BorderLayout());
							completionMenu.add(getCompletionPanel(suggestion));
							Point p = getUI().modelToView(this, caretPosition).getLocation();
							completionMenu.show(this, p.x, p.y + getFontMetrics(getFont()).getHeight());
						} catch (BadLocationException exception) {
							return;
						}
					});
					completionMenu.add(menuItem);
					Point p = getUI().modelToView(this, caretPosition).getLocation();
					completionMenu.show(this, p.x, p.y + getFontMetrics(getFont()).getHeight());
					return;
				} else if (suggestions.size() == 1) {
					completionMenu.setLayout(new BorderLayout());
					var suggestion = suggestions.getFirst();
					completionMenu.add(getCompletionPanel(suggestion));
					Point p = getUI().modelToView(this, caretPosition).getLocation();
					completionMenu.show(this, p.x, p.y + getFontMetrics(getFont()).getHeight());
					return;
				} else {
					completionMenu.setLayout(new BorderLayout());
					JPanel wrapper = new JPanel();
					wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
					var size = suggestions.size();
					for (int i = 0; i < size; i++) {
						var suggestion = suggestions.get(i);
						wrapper.add(getCompletionPanel(suggestion));
						if (i < size - 1) {
							wrapper.add(new JSeparator());
						}
					}
					wrapper.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

					wrapper.setPreferredSize(new Dimension(300, suggestions.size() * 80)); // adjust 80 to panel height

					completionMenu.removeAll();
					completionMenu.add(wrapper);
					Point p = getUI().modelToView(this, caretPosition).getLocation();
					completionMenu.show(this, p.x, p.y + getFontMetrics(getFont()).getHeight());
					return;
				}

			} else {
				completionMenu.setVisible(false);
			}
		} catch (BadLocationException ex) {
			ex.printStackTrace();
		}
	}

	private final java.util.List<FoldRegion> foldRegions = new ArrayList<>();

	public boolean isParagraphHidden(int startOffset) {
		Element root = getDocument().getDefaultRootElement();
		int line = root.getElementIndex(startOffset);
		for (FoldRegion fr : foldRegions) {
			if (!fr.collapsed) {
				continue;
			}
			int sLine = getLineFromOffset(fr.getStartOffset());
			int eLine = getLineFromOffset(fr.getEndOffset());
			// Hide lines between start and end, including end line
			if (line > sLine && line <= eLine) {
				return true;
			}
		}
		return false;
	}

	private int getLineFromOffset(int offset) {
		Element root = getDocument().getDefaultRootElement();
		return root.getElementIndex(Math.max(0, Math.min(offset, getDocument().getLength())));
	}

	private int getLineStartOffset(int line) {
		Element root = getDocument().getDefaultRootElement();
		line = Math.max(0, Math.min(line, root.getElementCount() - 1));
		return root.getElement(line).getStartOffset();
	}

	protected boolean isFoldStartLine(int line) {
		for (FoldRegion fr : foldRegions) {
			if (getLineFromOffset(fr.getStartOffset()) == line) {
				return true;
			}
		}
		return false;
	}

	protected boolean isFoldCollapsed(int line) {
		for (FoldRegion fr : foldRegions) {
			if (getLineFromOffset(fr.getStartOffset()) == line) {
				return fr.collapsed;
			}
		}
		return false;
	}

	protected boolean isLineVisible(int line) {
		Element root = getDocument().getDefaultRootElement();
		if (line < 0 || line >= root.getElementCount()) {
			return false;
		}
		try {
			int offset = root.getElement(line).getStartOffset();
			return !isParagraphHidden(offset);
		} catch (Exception e) {
			return true;
		}
	}

	protected int getDisplayLineNumber(int line) {
		// Calculate the displayed line number (skipping collapsed lines)
		// Count how many visible lines are before this line, plus 1 for this line if visible
		int displayLine = 1;
		for (int i = 0; i < line; i++) {
			if (isLineVisible(i)) {
				displayLine++;
			}
		}
		// If this line itself is not visible, return -1 (shouldn't happen, but handle it)
		if (!isLineVisible(line)) {
			return -1;
		}
		return displayLine;
	}

	protected int getHiddenLineCount(int line) {
		// Get the number of lines hidden by a fold starting at this line
		for (FoldRegion fr : foldRegions) {
			if (getLineFromOffset(fr.getStartOffset()) == line && fr.collapsed) {
				int startLine = getLineFromOffset(fr.getStartOffset());
				int endLine = getLineFromOffset(fr.getEndOffset());
				return Math.max(0, endLine - startLine);
			}
		}
		return 0;
	}

	protected int getHiddenLineCountAfterFold(int line) {
		// Check if this line is the first visible line after a collapsed fold
		// Returns the number of hidden lines, or 0 if not after a fold
		for (FoldRegion fr : foldRegions) {
			if (!fr.collapsed) {
				continue;
			}
			int foldStartLine = getLineFromOffset(fr.getStartOffset());
			int foldEndLine = getLineFromOffset(fr.getEndOffset());
			// Check if this line immediately follows the fold end
			if (line == foldEndLine + 1) {
				return Math.max(0, foldEndLine - foldStartLine);
			}
		}
		return 0;
	}

	protected void toggleFoldAtLine(int line) {
		// If already a region starting here, just toggle/remove
		for (Iterator<FoldRegion> it = foldRegions.iterator(); it.hasNext();) {
			FoldRegion fr = it.next();
			if (getLineFromOffset(fr.getStartOffset()) == line) {
				fr.collapsed = !fr.collapsed;
				invalidateAffectedViews(fr);
				return;
			}
		}
		// Create new region using triggers
		int[] region = findRegionFromLineUsingTriggers(line);
		if (region == null) {
			return;
		}
		try {
			Position startPos = getDocument().createPosition(getDocument().getDefaultRootElement().getElement(region[0]).getStartOffset());
			Position endPos = getDocument().createPosition(getDocument().getDefaultRootElement().getElement(region[1]).getStartOffset());
			FoldRegion newFr = new FoldRegion(startPos, endPos, true);
			foldRegions.add(newFr);
			invalidateAffectedViews(newFr);
		} catch (BadLocationException e) {
			// ignore
		}
	}

	private void invalidateAffectedViews(FoldRegion fr) {
		// Force complete view hierarchy invalidation and recalculation
		View rootView = getUI().getRootView(this);
		if (rootView != null) {
			// Get current component size
			int width = getWidth();
			int height = getHeight();
			if (width <= 0) {
				width = getPreferredSize().width;
			}
			if (height <= 0) {
				height = getPreferredSize().height;
			}

			// Force view hierarchy to recalculate by setting size and invalidating
			rootView.setSize(width, height);
			rootView.preferenceChanged(null, true, true);

			// Traverse view hierarchy and invalidate affected paragraph views
			invalidateViewHierarchy(rootView, fr);
		}

		// Invalidate and force layout recalculation
		invalidate();
		revalidate();

		// Force repaint
		repaint();

		// Also invalidate parent scroll pane if any
		Container parent = getParent();
		while (parent != null && !(parent instanceof JScrollPane)) {
			parent = parent.getParent();
		}
		if (parent != null) {
			parent.invalidate();
			parent.revalidate();
		}
	}

	private void invalidateViewHierarchy(View view, FoldRegion fr) {
		if (view == null) {
			return;
		}

		// If this is a paragraph view, check if it's affected
		if (view instanceof FoldingParagraphView) {
			try {
				int offset = view.getStartOffset();
				if (isParagraphHidden(offset)) {
					// This view should have 0 height - notify parent
					View parent = view.getParent();
					if (parent != null) {
						parent.preferenceChanged(view, true, true);
					}
				} else {
					// View should be visible - also notify parent in case it was hidden before
					View parent = view.getParent();
					if (parent != null) {
						parent.preferenceChanged(view, true, true);
					}
				}
			} catch (Exception ignored) {
			}
		}

		// Recursively invalidate children
		int count = view.getViewCount();
		for (int i = 0; i < count; i++) {
			View child = view.getView(i);
			if (child != null) {
				invalidateViewHierarchy(child, fr);
			}
		}
	}

	private int[] findRegionFromLineUsingTriggers(int line) {
		try {
			int lineStart = getLineStartOffset(line);
			int lineEnd = getDocument().getDefaultRootElement().getElement(line).getEndOffset();
			String lineText = getDocument().getText(lineStart, lineEnd - lineStart);

			// Try each trigger pair in order
			for (java.util.Map.Entry<String, String> pair : foldTriggerPairs.entrySet()) {
				String startSymbol = pair.getKey();
				String endSymbol = pair.getValue();
				int startIdx = lineText.indexOf(startSymbol);
				if (startIdx >= 0) {
					int[] region = findRegionFromOffset(lineStart + startIdx, startSymbol, endSymbol);
					if (region != null) {
						return region;
					}
				}
			}
		} catch (BadLocationException ignored) {
		}
		return null;
	}

	private int[] findRegionFromOffset(int startOffset, String startSymbol, String endSymbol) {
		try {
			int docLen = getDocument().getLength();
			int pos = startOffset + startSymbol.length();
			int startLine = getLineFromOffset(startOffset);

			// For single-character symbols, use simple stack-based matching
			if (startSymbol.length() == 1 && endSymbol.length() == 1) {
				char startChar = startSymbol.charAt(0);
				char endChar = endSymbol.charAt(0);
				int depth = 1;
				for (; pos < docLen; pos++) {
					char c = getDocument().getText(pos, 1).charAt(0);
					if (c == startChar) {
						depth++;
					} else if (c == endChar) {
						depth--;
						if (depth == 0) {
							int endLine = getLineFromOffset(pos);
							return new int[]{startLine, endLine};
						}
					}
				}
			} else {
				// For multi-character symbols (like /* */)
				int endLen = endSymbol.length();
				int depth = 1;
				for (; pos < docLen - endLen + 1; pos++) {
					String substr = getDocument().getText(pos, Math.min(endLen, docLen - pos));
					if (substr.equals(endSymbol)) {
						depth--;
						if (depth == 0) {
							int endLine = getLineFromOffset(pos + endLen - 1);
							return new int[]{startLine, endLine};
						}
					}
					// Also check for nested start symbols
					if (pos + startSymbol.length() <= docLen) {
						String startSubstr = getDocument().getText(pos, Math.min(startSymbol.length(), docLen - pos));
						if (startSubstr.equals(startSymbol)) {
							depth++;
							pos += startSymbol.length() - 1; // Skip ahead to avoid re-checking
						}
					}
				}
			}
		} catch (BadLocationException e) {
			// ignore
		}
		return null;
	}

	public void setAutoFoldingEnabled(boolean enabled) {
		this.autoFoldingEnabled = enabled;
	}

	public void setFoldTriggerPairs(java.util.Map<String, String> pairs) {
		this.foldTriggerPairs.clear();
		if (pairs != null) {
			this.foldTriggerPairs.putAll(pairs);
		}
		if (autoFoldingEnabled) {
			rebuildFoldRegions();
		}
	}

	public void setFoldTriggerPair(String start, String end) {
		this.foldTriggerPairs.put(start, end);
		if (autoFoldingEnabled) {
			rebuildFoldRegions();
		}
	}

	public java.util.Map<String, String> getFoldTriggerPairs() {
		return java.util.Collections.unmodifiableMap(foldTriggerPairs);
	}

	private void rebuildFoldRegions() {
		// Preserve collapsed state by start-line when possible
		java.util.Set<Integer> collapsedStarts = new java.util.HashSet<>();
		for (FoldRegion fr : foldRegions) {
			if (fr.collapsed) {
				collapsedStarts.add(getLineFromOffset(fr.getStartOffset()));
			}
		}
		foldRegions.clear();

		try {
			String text = getDocument().getText(0, getDocument().getLength());
			int len = text.length();
			Element root = getDocument().getDefaultRootElement();

			// Process each trigger pair
			for (java.util.Map.Entry<String, String> pair : foldTriggerPairs.entrySet()) {
				String startSymbol = pair.getKey();
				String endSymbol = pair.getValue();

				if (startSymbol.length() == 1 && endSymbol.length() == 1) {
					// Single-character symbols - use stack-based matching
					char startChar = startSymbol.charAt(0);
					char endChar = endSymbol.charAt(0);
					java.util.Deque<Integer> stack = new java.util.ArrayDeque<>();
					for (int i = 0; i < len; i++) {
						char ch = text.charAt(i);
						if (ch == startChar) {
							stack.push(i);
						} else if (ch == endChar) {
							if (!stack.isEmpty()) {
								int start = stack.pop();
								int startLine = getLineFromOffset(start);
								int endLine = getLineFromOffset(i);
								if (endLine > startLine) {
									Position sp = getDocument().createPosition(root.getElement(startLine).getStartOffset());
									Position ep = getDocument().createPosition(root.getElement(endLine).getStartOffset());
									FoldRegion fr = new FoldRegion(sp, ep, collapsedStarts.contains(startLine));
									foldRegions.add(fr);
								}
							}
						}
					}
				} else {
					// Multi-character symbols - scan linearly
					int startLen = startSymbol.length();
					int endLen = endSymbol.length();
					int i = 0;
					while (i < len - startLen + 1) {
						String substr = text.substring(i, Math.min(i + startLen, len));
						if (substr.equals(startSymbol)) {
							int start = i;
							i += startLen;
							int depth = 1;
							while (i < len - endLen + 1 && depth > 0) {
								String endSubstr = text.substring(i, Math.min(i + endLen, len));
								if (endSubstr.equals(endSymbol)) {
									depth--;
									if (depth == 0) {
										int end = i + endLen - 1;
										int startLine = getLineFromOffset(start);
										int endLine = getLineFromOffset(end);
										if (endLine > startLine) {
											Position sp = getDocument().createPosition(root.getElement(startLine).getStartOffset());
											Position ep = getDocument().createPosition(root.getElement(endLine).getStartOffset());
											FoldRegion fr = new FoldRegion(sp, ep, collapsedStarts.contains(startLine));
											foldRegions.add(fr);
										}
										i = end + 1;
										break;
									}
								} else if (i + startLen <= len) {
									String nextStartSubstr = text.substring(i, Math.min(i + startLen, len));
									if (nextStartSubstr.equals(startSymbol)) {
										depth++;
										i += startLen;
										continue;
									}
								}
								i++;
							}
							continue;
						}
						i++;
					}
				}
			}
		} catch (BadLocationException ignored) {
		}

		repaint();
	}

	@Override
	public void setCaretPosition(int position) {
		try {
			Element root = getDocument().getDefaultRootElement();
			int line = root.getElementIndex(position);
			if (isParagraphHidden(root.getElement(line).getStartOffset())) {
				// snap to start line of the fold
				for (FoldRegion fr : foldRegions) {
					if (!fr.collapsed) {
						continue;
					}
					int sLine = getLineFromOffset(fr.getStartOffset());
					int eLine = getLineFromOffset(Math.max(fr.getEndOffset() - 1, 0));
					if (line > sLine && line <= eLine) {
						super.setCaretPosition(root.getElement(sLine).getStartOffset());
						return;
					}
				}
			}
		} catch (Exception ignored) {
		}
		super.setCaretPosition(position);
	}

}
