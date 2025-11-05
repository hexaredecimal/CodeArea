package org.libcode;

import org.libcode.lines.LineNumberMode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.border.Border;
import org.libcode.intelisense.IntellisenseItem;
import org.libcode.layers.Layer;
import org.libcode.layers.LayerIndex;
import org.libcode.layers.LayerRender;
import org.libcode.ui.Gutter;
import org.libcode.ui.CodePane;

/**
 *
 * @author hexaredecimal
 */
public final class CodeArea extends JPanel {
	private final CodePane codeArea;
	private final Gutter gutter;

	public CodeArea() {
		this.codeArea = new CodePane();
		JScrollPane scrollPane = new JScrollPane(this.codeArea);
		this.gutter = new Gutter(this.codeArea, scrollPane);
		scrollPane.setRowHeaderView(this.gutter);
		setLayout(new BorderLayout());
		this.add(scrollPane, BorderLayout.CENTER);

		this.hideLayer(LayerIndex.GRIDX_LAYER);
		this.hideLayer(LayerIndex.GRIDY_LAYER);
	}


	public void addLayer(int layer, LayerRender render) {
		codeArea.addLayer(layer, render);
	}

	public JTextPane getCodeArea() {
		return codeArea;
	}

	public Gutter getGutter() {
		return gutter;
	}

	
	public void showLayer(int index) {
		codeArea.showLayer(index);
	}

	public void hideLayer(int index) {
		codeArea.hideLayer(index);
	}

	public Layer getLayer(int index) {
		return codeArea.getLayer(index);
	}

	public boolean canUndo() {
		return codeArea.canUndo();
	}

	public boolean canRedo() {
		return codeArea.canRedo();
	}

	public void undo() {
		codeArea.undo();
	}

	public void redo() {
		codeArea.redo();
	}

	public void copy() {
		codeArea.copy();
	}

	public void paste() {
		codeArea.paste();
	}

	public int getLine() {
		return codeArea.getLine();
	}

	public void markErrorAtLine(int line, String message) {
		codeArea.markErrorLine(line - 1, message);
	}

	public void unmarkErrorAtLine(int line) {
		codeArea.unmarkErrorLine(line);
	}
	
	public void clearErrorLines() {
		codeArea.clearErrorLines();
	}

	public void enableCodeFolding(boolean activate) {
		codeArea.setAutoFoldingEnabled(activate);
	}

	public void addFoldSymbolPair(String startSymbol, String endSymbol) {
		codeArea.setFoldTriggerPair(startSymbol, endSymbol);
	}

	public void addFoldSymbolPairs(HashMap<String, String> pairs) {
		codeArea.setFoldTriggerPairs(pairs);
	}

	public Map<String, String> getFoldSymbolPairs() {
		return codeArea.getFoldTriggerPairs();
	}

	public void setLineMode(LineNumberMode mode) {
		gutter.setMode(mode);
	}

	public void setAbsoluteLineMode() {
		gutter.setMode(LineNumberMode.ABSOLUTE);
	}

	public void setRelativeLineMode() {
		gutter.setMode(LineNumberMode.RELATIVE);
	}

	public void addHighlightedWords(List<String> words, Color color, boolean bold, boolean italic) {
		codeArea.addHighlightedWords(words, color, bold, italic);
	}

	public void addHighlightedWords(List<String> words, Color color) {
		codeArea.addHighlightedWords(words, color, false, false);
	}

	public void addCompletions(List<IntellisenseItem> completions) {
		codeArea.addCompletions(completions);
	}

	public void addCompletion(IntellisenseItem completion) {
		codeArea.addCompletion(completion);
	}
}
