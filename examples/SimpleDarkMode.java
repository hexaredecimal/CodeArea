

import java.awt.Color;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.libcode.CodeArea;
import org.libcode.colors.Colors;
import org.libcode.intelisense.IntellisenseItem;
import org.libcode.layers.LayerIndex;

/**
 *
 * @author hexaredecimal
 */
public class SimpleDarkMode extends JFrame {

	public SimpleDarkMode() {
		setTitle("Custom Editor");
		setSize(800, 600);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);

		var editor = new CodeArea();

		// keywords
		editor.addHighlightedWords(List.of("open", "let", "if", "then", "else", "when", "is", "type", "where"), new Color(0x20c328e3));
		editor.addHighlightedWords(List.of("true", "false"), new Color(60, 240, 60));
		editor.addHighlightedWords(List.of("Number", "String", "Boolean", "Unit"), new Color(60, 60, 150));

		editor.addFoldSymbolPair("=", ";");
		editor.showLayer(LayerIndex.GRIDX_LAYER);
		editor.showLayer(LayerIndex.GRIDY_LAYER);

		editor.markErrorAtLine(1, "Eh?");
		editor.addCompletion(new IntellisenseItem("add", "Number -> Number -> Number", "Adds 2 numbers"));
		editor.addCompletion(new IntellisenseItem("sumList", "[Number] -> Number", "Sums a list of numbers into a single value"));
		add(editor);
		
		Colors.setBackgroundColor(new Color(18, 18, 18));
		Colors.setGutterBackgroundColor(new Color(18*2, 18*2, 18*2));
		Colors.setGutterForegroundColor(new Color(255 - 18, 255 - 18, 255-18));
		Colors.setForegroundColor(Color.WHITE);
		Colors.setCurrentLineColor(new Color(140, 25, 130, 60));
		Colors.setErrorLeftGradientColor(Colors.getBackgroundColor());
		Colors.setErrorRightGradientColor(new Color(160, 20, 20, 30));
		Colors.setErrorTextColor(Color.WHITE);
		Colors.setGridColor(new Color(30, 30, 30));
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			var editor = new SimpleDarkMode();
			editor.setVisible(true);
		});
	}
}
