package com.example;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.undo.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class CustomEditor extends JFrame {

    public CustomEditor() {
        setTitle("Custom Editor");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        CustomTextPane textPane = new CustomTextPane();
        JScrollPane scrollPane = new JScrollPane(textPane);
        LineNumberGutter gutter = new LineNumberGutter(textPane, scrollPane);
        scrollPane.setRowHeaderView(gutter);

        textPane.hideLayer(CustomTextPane.GRIDY_LAYER);
        textPane.hideLayer(CustomTextPane.GRIDX_LAYER);

        add(scrollPane);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            CustomEditor editor = new CustomEditor();
            editor.setVisible(true);
        });
    }
}

@FunctionalInterface
interface LayerRender {
  void draw(int width, int height, Graphics2D g2d);
}

class Layer {
  public int layer;
  public LayerRender render;
  public boolean active;

  public Layer(int layer, LayerRender render) {
    this.layer = layer;
    this.render = render;
    this.active = true;
  }
}

class CustomTextPane extends JTextPane {

  private final Set<Integer> errorLines = new HashSet<>();
  private final Map<Integer, String> errorMessages = new HashMap<>();
  private final List<String> keywords = List.of("abstract", "continue", "for", "new", "switch", "assert", "default", "goto", "package", "synchronized", "boolean", "do", "if", "private", "this", "break", "double", "implements", "protected", "throw", "byte", "else", "import", "public", "throws", "case", "enum", "instanceof", "return", "transient", "catch", "extends", "int", "short", "try", "char", "final", "interface", "static", "void", "class", "finally", "long", "strictfp", "volatile", "const", "float", "native", "super", "while");

  private final List<CompletionItem> docCompletions = List.of(
      new CompletionItem("println", "void println(String x)", "Prints a String and then terminate the line."),
      new CompletionItem("print", "void print(String x)", "Prints a String."),
      new CompletionItem("format", "static String format(String format, Object... args)", "Returns a formatted string using the specified format string and arguments."),
      new CompletionItem("System", "The java System object", "class java.lang.System"),
      new CompletionItem("out", "The print stream", "Return an reference to an object of PrintStream"),
      new CompletionItem("String", "The java String class", "class java.lang.String")
      );

  private JPopupMenu completionMenu;
  private final Style keywordStyle;
  private int selectedSuggestionIndex = 0;
  private final Font editorFont = new Font("Monospaced", Font.PLAIN, 14);
  private int currentLine = 0;
  UndoManager undoManager = new UndoManager();
  private BufferedImage backgroundImage;

  private List<Layer> layers;
  private boolean autoFoldingEnabled = true;
  // Map of start symbols to end symbols for folding (e.g., "{" -> "}", "[" -> "]", "begin" -> "end")
  private final java.util.Map<String, String> foldTriggerPairs = new java.util.LinkedHashMap<>();

  public static int FREE_LAYER = Integer.MIN_VALUE;
  public static int GRIDX_LAYER = 0;
  public static int GRIDY_LAYER = 1;
  public static int LINE_SELECT_LAYER = 2;

  public CustomTextPane() {
    setFont(editorFont);
    setOpaque(false);
    StyledDocument doc = getStyledDocument();
    Style defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
    StyleConstants.setFontFamily(defaultStyle, "Monospaced");
    StyleConstants.setFontSize(defaultStyle, 14);

    keywordStyle = doc.addStyle("KeywordStyle", defaultStyle);
    StyleConstants.setForeground(keywordStyle, new Color(0, 0, 128));
    StyleConstants.setBold(keywordStyle, true);
    layers = new ArrayList<>();
    // install folding-aware editor kit
    setEditorKit(new FoldingEditorKit(this));
    
    // Initialize default fold trigger pairs
    foldTriggerPairs.put("{", "}");
    foldTriggerPairs.put("/*", "*/");
    foldTriggerPairs.put("[", "]");
    try {
            // Load image from the 'fonts' folder
            backgroundImage = ImageIO.read(new File("images/bg.png"));
        } catch (Exception e) {
            e.printStackTrace();
        }

    setupCompletionMenu();
    setupContextMenu();
    // initial fold region build
    if (autoFoldingEnabled) {
      SwingUtilities.invokeLater(this::rebuildFoldRegions);
    }

    getDocument().addUndoableEditListener(e -> {
      UndoableEdit edit = e.getEdit();
      if (edit instanceof AbstractDocument.DefaultDocumentEvent docEvent) {
        if (docEvent.getType() == DocumentEvent.EventType.INSERT ||
            docEvent.getType() == DocumentEvent.EventType.REMOVE) {
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
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_E) {
          int caretPos = ed.getCaretPosition();
          int currentLine = ed.getDocument().getDefaultRootElement().getElementIndex(caretPos);
          if (errorLines.contains(currentLine)) {
            errorLines.remove(currentLine);
          } else markErrorLine(currentLine, "Woah! Look at this error at line " + (currentLine + 1));
          e.consume();
        } if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_R) {
          clearErrorLines();
          e.consume();
        } else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_SPACE) {
          showDocCompletion();
          e.consume();
        } else if (completionMenu.isVisible()) {
          handleCompletionNavigation(e);
        }else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          try {
            int caretPos = getCaretPosition();
            int lineStart = Utilities.getRowStart(CustomTextPane.this, caretPos);
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
            ex.printStackTrace();
          }
        }

      }

      @Override
      public void keyReleased(KeyEvent e) {
        if (e.isControlDown() || e.getKeyCode() == KeyEvent.VK_SPACE) return;

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

    addLayer(LINE_SELECT_LAYER, (w, h, g2) -> {
      int lineCount = ed.getDocument().getDefaultRootElement().getElementCount();
      int y = -1;

      try {
        y = modelToView(getSelectionStart()).y;
      } catch (BadLocationException e) {
        // ignore
      }

      if (y != -1) {

        g2.setColor(new Color(230, 230, 240));
        g2.fillRect(0, y - 2, w, lineHeight);
      }
    });

    addLayer(GRIDX_LAYER, (w, h, g2) -> {
      g2.setColor(new Color(220, 220, 220));
      int gridSize = fontMetrics.getHeight();

      if (gridSize > 0) {
        for (int x = 0; x < w; x += gridSize) {
          g2.drawLine(x, 0, x, h);
        }
      }
    });


    addLayer(GRIDY_LAYER, (w, h, g2) -> {
      g2.setColor(new Color(220, 220, 220));
      int gridSize = fontMetrics.getHeight();

      if (gridSize > 0) {
        for (int y_ = 0; y_ < h; y_ += gridSize) {
          g2.drawLine(0, y_, h, y_);
        }
      }
    });


    // folded inline indicator layer: draw " {...}" at end of the start line when collapsed
    addLayer(Integer.MAX_VALUE - 1, (w, h, g2) -> {
      g2.setFont(getFont());
      final FontMetrics fm2 = getFontMetrics(getFont());
      for (FoldRegion fr : foldRegions) {
        if (!fr.collapsed) continue;
        int startLine = getLineFromOffset(fr.getStartOffset());
        try {
          Element rootEl = getDocument().getDefaultRootElement();
          Element lineEl = rootEl.getElement(startLine);
          if (lineEl == null) continue;
          int lineStart = lineEl.getStartOffset();
          int lineEnd = lineEl.getEndOffset();
          Rectangle rStart = modelToView(lineStart);
          Rectangle rEnd = modelToView(Math.max(lineStart, lineEnd - 1));
          if (rStart == null || rEnd == null) continue;
          // String label = " {...}";
          // int x = rEnd.x + 4;
          // int y = rEnd.y + fm2.getAscent();
          // g2.setColor(new Color(70, 70, 90));
          // g2.drawString(label, x, y);
        } catch (BadLocationException ignored) {}
      }
    });

    addLayer(Integer.MAX_VALUE, (w, h, g2) -> {
      int lineCount = ed.getDocument().getDefaultRootElement().getElementCount();
      for (int line : errorLines) {
        if (line < 0 || line >= lineCount) continue;
        try {
          Rectangle r = modelToView(getDocument().getDefaultRootElement().getElement(line).getStartOffset());
          if (r == null) continue;

          // Gradient background: left (white) → right (light red)
          Color bg = getBackground();
          GradientPaint gp = new GradientPaint(
              0, r.y, new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 0), 
              getWidth(), r.y, new Color(255, 150, 150, 120)
              );
          g2.setPaint(gp);
          g2.fillRect(0, r.y - 2, w, lineHeight);

          // Draw error message (right side, bright red)
          String message = errorMessages.get(line);
          if (message != null && !message.isEmpty()) {
            g2.setColor(new Color(220, 0, 0));
            int textWidth = fontMetrics.stringWidth(message);
            g2.drawString(message, getWidth() - textWidth - 10, r.y + fontMetrics.getAscent() - 2);
          }
        } catch (BadLocationException ignored) {}
      }
    });


  }

  public void addLayer(int layer, LayerRender render) {
    layers.add(new Layer(layer, render));
    layers.sort((l1, l2) -> Integer.compare(l1.layer, l2.layer));
  }

  public void addLayer(LayerRender render) {
    layers.add(new Layer(FREE_LAYER++, render));
    layers.sort((l1, l2) -> Integer.compare(l1.layer, l2.layer));
  }


  public Layer getLayer(int index) {
    for (var layer: layers) {
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
    System.out.println("Hiding layer: " + layer);
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
    JMenuItem docCompletionItem = new JMenuItem("Show Documentation");
    docCompletionItem.addActionListener(e -> showDocCompletion());

    JMenuItem _undo = new JMenuItem("Undo");
    _undo.addActionListener(e -> { if (canUndo()) undo(); } );

    JMenuItem _redo = new JMenuItem("Redo");
    _redo.addActionListener(e -> {  if(canRedo()) redo(); } );

    contextMenu.add(docCompletionItem);
    contextMenu.add(new JSeparator());
    contextMenu.add(_undo);
    contextMenu.add(_redo);
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
    if (suggestionCount == 0) return;

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
    if (message != null && !message.isEmpty())
      errorMessages.put(line, message);
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
    g.setColor(new Color(250, 250, 250));
    g.fillRect(0, 0, getWidth(), getHeight());
    Graphics2D g2 = (Graphics2D) g.create();
    for (var layer: layers) {
      if (layer.active)
        layer.render.draw(getWidth(), getHeight(), g2);
    }
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

    for (String keyword : keywords) {
      int pos = 0;
      while ((pos = text.indexOf(keyword, pos)) >= 0) {
        if (isWholeWord(text, pos, keyword.length())) {
          doc.setCharacterAttributes(pos, keyword.length(), keywordStyle, true);
        }
        pos += keyword.length();
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
      suggestions.addAll(keywords.stream().filter(k -> k.startsWith(cw)).collect(Collectors.toList()));
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

  private JPanel getCompletionPanel(CompletionItem suggestion) {
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
        if (!Character.isLetterOrDigit(c) && c != '_') break; // stop at non-identifier
        wordStart--;
      }

      int wordEnd = caretPosition;
      while (wordEnd < text.length()) {
        char c = text.charAt(wordEnd);
        if (!Character.isLetterOrDigit(c) && c != '_') break;
        wordEnd++;
      }

      if (wordStart >= wordEnd) {
        completionMenu.setVisible(false);
        return;
      }

      String currentWord = text.substring(wordStart, wordEnd);
      List<CompletionItem> _suggestions = docCompletions.stream()
        .filter(c -> c.getName().startsWith(currentWord))
        .sorted((c1, c2) -> {
          if (c1.getName().equals(currentWord)) return -1;
          if (c2.getName().equals(currentWord)) return 1;
          return c1.getName().compareTo(c2.getName());
        })
      .collect(Collectors.toList());

      List<CompletionItem> suggestions = new ArrayList<>(_suggestions);
      for (var suggestion: suggestions) {
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
        }else {
          completionMenu.setLayout(new BorderLayout());
          JPanel wrapper = new JPanel();
          wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
          var size = suggestions.size();
          for (int i = 0; i < size; i++) {
            var suggestion = suggestions.get(i);
            wrapper.add(getCompletionPanel(suggestion));
            if (i < size - 1)
              wrapper.add(new JSeparator());
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
  // ===== Folding support =====
  private static class FoldRegion {
    private final Position startPos; // start line (visible), region hides lines after this up to end
    private final Position endPos;   // end line inclusive
    private boolean collapsed;

    FoldRegion(Position startPos, Position endPos, boolean collapsed) {
      this.startPos = startPos;
      this.endPos = endPos;
      this.collapsed = collapsed;
    }

    int getStartOffset() { return startPos.getOffset(); }
    int getEndOffset() { return endPos.getOffset(); }
  }

  private final java.util.List<FoldRegion> foldRegions = new ArrayList<>();

  public boolean isParagraphHidden(int startOffset) {
    Element root = getDocument().getDefaultRootElement();
    int line = root.getElementIndex(startOffset);
    for (FoldRegion fr : foldRegions) {
      if (!fr.collapsed) continue;
      int sLine = getLineFromOffset(fr.getStartOffset());
      int eLine = getLineFromOffset(fr.getEndOffset());
      // Hide lines between start and end, including end line
      if (line > sLine && line <= eLine) return true;
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

  public boolean isFoldStartLine(int line) {
    for (FoldRegion fr : foldRegions) {
      if (getLineFromOffset(fr.getStartOffset()) == line) return true;
    }
    return false;
  }

  public boolean isFoldCollapsed(int line) {
    for (FoldRegion fr : foldRegions) {
      if (getLineFromOffset(fr.getStartOffset()) == line) return fr.collapsed;
    }
    return false;
  }
  
  public boolean isLineVisible(int line) {
    Element root = getDocument().getDefaultRootElement();
    if (line < 0 || line >= root.getElementCount()) return false;
    try {
      int offset = root.getElement(line).getStartOffset();
      return !isParagraphHidden(offset);
    } catch (Exception e) {
      return true;
    }
  }
  
  public int getDisplayLineNumber(int line) {
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
  
  public int getHiddenLineCount(int line) {
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
  
  public int getHiddenLineCountAfterFold(int line) {
    // Check if this line is the first visible line after a collapsed fold
    // Returns the number of hidden lines, or 0 if not after a fold
    for (FoldRegion fr : foldRegions) {
      if (!fr.collapsed) continue;
      int foldStartLine = getLineFromOffset(fr.getStartOffset());
      int foldEndLine = getLineFromOffset(fr.getEndOffset());
      // Check if this line immediately follows the fold end
      if (line == foldEndLine + 1) {
        return Math.max(0, foldEndLine - foldStartLine);
      }
    }
    return 0;
  }

  public void toggleFoldAtLine(int line) {
    // If already a region starting here, just toggle/remove
    for (Iterator<FoldRegion> it = foldRegions.iterator(); it.hasNext(); ) {
      FoldRegion fr = it.next();
      if (getLineFromOffset(fr.getStartOffset()) == line) {
        fr.collapsed = !fr.collapsed;
        invalidateAffectedViews(fr);
        return;
      }
    }
    // Create new region using triggers
    int[] region = findRegionFromLineUsingTriggers(line);
    if (region == null) return;
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
      if (width <= 0) width = getPreferredSize().width;
      if (height <= 0) height = getPreferredSize().height;
      
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
    if (view == null) return;
    
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
      } catch (Exception ignored) {}
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
          if (region != null) return region;
        }
      }
    } catch (BadLocationException ignored) {}
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
          if (c == startChar) depth++;
          else if (c == endChar) {
            depth--;
            if (depth == 0) {
              int endLine = getLineFromOffset(pos);
              return new int[] { startLine, endLine };
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
              return new int[] { startLine, endLine };
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
    if (pairs != null) this.foldTriggerPairs.putAll(pairs);
    if (autoFoldingEnabled) rebuildFoldRegions();
  }

  public java.util.Map<String, String> getFoldTriggerPairs() {
    return java.util.Collections.unmodifiableMap(foldTriggerPairs);
  }
  
  @Deprecated
  public void setFoldTriggers(java.util.Set<String> triggers) {
    // Legacy support - convert to pairs (only supports single-char triggers)
    foldTriggerPairs.clear();
    for (String trigger : triggers) {
      if (trigger.equals("{")) foldTriggerPairs.put("{", "}");
      else if (trigger.equals("/*")) foldTriggerPairs.put("/*", "*/");
      else if (trigger.equals("[")) foldTriggerPairs.put("[", "]");
    }
    if (autoFoldingEnabled) rebuildFoldRegions();
  }
  
  @Deprecated
  public java.util.Set<String> getFoldTriggers() {
    return java.util.Collections.unmodifiableSet(foldTriggerPairs.keySet());
  }

  private void rebuildFoldRegions() {
    // Preserve collapsed state by start-line when possible
    java.util.Set<Integer> collapsedStarts = new java.util.HashSet<>();
    for (FoldRegion fr : foldRegions) {
      if (fr.collapsed) collapsedStarts.add(getLineFromOffset(fr.getStartOffset()));
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
            if (ch == startChar) stack.push(i);
            else if (ch == endChar) {
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
    } catch (BadLocationException ignored) {}

    repaint();
  }

  // optional: keep caret out of hidden lines
  @Override
  public void setCaretPosition(int position) {
    try {
      Element root = getDocument().getDefaultRootElement();
      int line = root.getElementIndex(position);
      if (isParagraphHidden(root.getElement(line).getStartOffset())) {
        // snap to start line of the fold
        for (FoldRegion fr : foldRegions) {
          if (!fr.collapsed) continue;
          int sLine = getLineFromOffset(fr.getStartOffset());
          int eLine = getLineFromOffset(Math.max(fr.getEndOffset() - 1, 0));
          if (line > sLine && line <= eLine) {
            super.setCaretPosition(root.getElement(sLine).getStartOffset());
            return;
          }
        }
      }
    } catch (Exception ignored) {}
    super.setCaretPosition(position);
  }
}

// Editor kit and paragraph view for folding
class FoldingEditorKit extends StyledEditorKit {
  private final ViewFactory factory;

  public FoldingEditorKit(CustomTextPane pane) {
    this.factory = new ViewFactory() {
      private final ViewFactory delegate = new StyledEditorKit().getViewFactory();
      @Override
      public View create(Element elem) {
        String kind = elem.getName();
        if (AbstractDocument.ParagraphElementName.equals(kind)) {
          return new FoldingParagraphView(elem, pane);
        }
        return delegate.create(elem);
      }
    };
  }

  @Override
  public ViewFactory getViewFactory() {
    return factory;
  }
}

class FoldingParagraphView extends ParagraphView {
  private final CustomTextPane pane;
  public FoldingParagraphView(Element elem, CustomTextPane pane) {
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

enum LineNumberMode {
    ABSOLUTE,
    RELATIVE
}

class LineNumberGutter extends JComponent {
  private final JTextPane textPane;
  private final Font gutterFont = new Font("Monospaced", Font.PLAIN, 14);
  private final Set<Integer> breakpoints = new HashSet<>();
  private final Set<Integer> bookmarks = new HashSet<>();
  private LineNumberMode mode = LineNumberMode.ABSOLUTE;
  private int scrollY = 0;

  public LineNumberGutter(JTextPane textPane, JScrollPane scrollPane) {
    this.textPane = textPane;

    scrollPane.getVerticalScrollBar().getModel().addChangeListener(e -> repaint());

    // Repaint on document change or caret move
    this.textPane.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) { 
        updateWidth();
        repaint(); 
      }
      public void removeUpdate(DocumentEvent e) { 
        updateWidth();
        repaint(); 
      }
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
    
    // Add mouse listener for fold marker clicks
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (textPane instanceof CustomTextPane ctp) {
          int line = getLineAtClick(e.getY());
          if (line >= 0 && ctp.isFoldStartLine(line)) {
            // Check if click is in the fold marker area (x between 10-28)
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
        if (viewport == null) return -1;
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
      System.out.println("Breakpoint: " + line);
      if (line >= 0) {
        if (breakpoints.contains(line)) breakpoints.remove(line);
        else breakpoints.add(line);
        repaint();
      }
    });
    contextMenu.add(toggleBreakpointItem);

    JMenuItem toggleBookmarkItem = new JMenuItem("Toggle Bookmark");
    toggleBookmarkItem.addActionListener(e -> {
      int line = getLineAtMouse();
      System.out.println("Bookmark: " + line);
      if (line >= 0) {
        if (bookmarks.contains(line)) bookmarks.remove(line);
        else bookmarks.add(line);
        repaint();
      }
    });
    contextMenu.add(toggleBookmarkItem);
    
    // Folding actions
    JMenuItem foldBlockItem = new JMenuItem("Fold Block");
    foldBlockItem.addActionListener(e -> {
      int line = getLineAtMouse();
      if (line >= 0 && textPane instanceof CustomTextPane ctp) {
        ctp.toggleFoldAtLine(line);
        repaint();
      }
    });
    contextMenu.add(foldBlockItem);
    
    JMenuItem unfoldBlockItem = new JMenuItem("Unfold Block");
    unfoldBlockItem.addActionListener(e -> {
      int line = getLineAtMouse();
      if (line >= 0 && textPane instanceof CustomTextPane ctp) {
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
        if (number > maxNumber) maxNumber = number;
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
    if (mousePos == null) return -1;
    int lineHeight = textPane.getFontMetrics(textPane.getFont()).getHeight();
    int scrollOffset = ((JViewport) getParent()).getViewPosition().y; // adjust for scroll
    return (mousePos.y + scrollOffset) / lineHeight;
  }


@Override
protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    g.setColor(new Color(230, 230, 230));
    g.fillRect(0, 0, getWidth(), getHeight());

    FontMetrics fm = textPane.getFontMetrics(textPane.getFont());
    int lineHeight = fm.getHeight();

    int lineCount = textPane.getDocument().getDefaultRootElement().getElementCount();
    int caretPos = textPane.getCaretPosition();
    int currentLine = textPane.getDocument().getDefaultRootElement().getElementIndex(caretPos);

    g.setFont(gutterFont);

    if (textPane instanceof CustomTextPane ctp) {
      // Paint visible lines continuously without gaps
      int visualY = 0; // Track visual Y position (no gaps)
      for (int i = 0; i < lineCount; i++) {
        if (!ctp.isLineVisible(i)) continue; // Skip collapsed lines
        
        int lineY = visualY * lineHeight + fm.getAscent();
        int lineYTop = visualY * lineHeight;

        // Highlight current line
        if (i == currentLine) {
            g.setColor(new Color(210, 210, 220));
            g.fillRect(0, lineYTop, getWidth(), lineHeight);
        }

        // Line numbers - use displayed line number (skipping collapsed lines)
        int displayNum = ctp.getDisplayLineNumber(i);
        String lineNumber = (mode == LineNumberMode.ABSOLUTE) ? String.valueOf(displayNum)
                : (i == currentLine ? "0" : String.valueOf(Math.abs(displayNum - ctp.getDisplayLineNumber(currentLine))));
        int stringWidth = fm.stringWidth(lineNumber);
        g.setColor(new Color(100, 100, 100));
        g.drawString(lineNumber, getWidth() - stringWidth - 10, lineY);

        // Breakpoints (use original line number)
        if (breakpoints.contains(i + 1)) {
            g.setColor(Color.RED);
            g.fillOval(5, lineYTop + lineHeight / 2 - 4, 8, 8);
        }

        // Bookmarks (use original line number)
        if (bookmarks.contains(i + 1)) {
            g.setColor(Color.BLUE);
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
            g.setColor(new Color(90, 90, 110));
            g.fillPolygon(tri);
        }
        
        // Check if this line is the first visible line after a collapsed fold
        int hiddenCount = ctp.getHiddenLineCountAfterFold(i);
        if (hiddenCount > 0) {
            // Draw the count indicator next to the line number
            String foldText = "+" + hiddenCount;
            int _stringWidth = fm.stringWidth(foldText);
            g.setColor(new Color(120, 120, 140));
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
        g.setColor(new Color(100, 100, 100));
        g.drawString(lineNumber, getWidth() - stringWidth - 10, lineY);
      }
    }
}



}


class CompletionItem {
    private final String name;
    private final String signature;
    private final String documentation;

    public CompletionItem(String name, String signature, String documentation) {
        this.name = name;
        this.signature = signature;
        this.documentation = documentation;
    }

    public String getName() {
        return name;
    }

    public String getSignature() {
        return signature;
    }

    public String getDocumentation() {
        return documentation;
    }
}
