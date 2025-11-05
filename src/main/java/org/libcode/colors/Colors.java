package org.libcode.colors;

import java.awt.Color;

/**
 *
 * @author hexaredecimal
 */
public class Colors {

	private static Color currentLineColor = new Color(230, 230, 240);
	private static Color errorTextColor = new Color(220, 0, 0);
	private static Color backgroundColor = new Color(255, 255, 255);
	private static Color foregroundColor = new Color(0, 0, 0);
	private static Color errorLeftGradientColor = new Color(255, 255, 255);
	private static Color errorRightGradientColor = new Color(255, 150, 150, 120);

	private static Color foldToggleColor = new Color(90, 90, 110);
	private static Color breakPointColor = Color.RED;
	private static Color bookmarkColor = Color.BLUE;
	private static Color gutterForegroundColor = new Color(100, 100, 100);
	private static Color gutterBackgroundColor = new Color(230, 230, 230);

	private static Color gridColor =  new Color(220, 220, 220);  

	public static Color getGridColor() {
		return gridColor;
	}

	public static void setGridColor(Color gridColor) {
		Colors.gridColor = gridColor;
	}
	
	public static Color getErrorLeftGradientColor() {
		return errorLeftGradientColor;
	}

	public static void setErrorLeftGradientColor(Color errorLeftGradientColor) {
		Colors.errorLeftGradientColor = errorLeftGradientColor;
	}

	public static Color getErrorRightGradientColor() {
		return errorRightGradientColor;
	}

	public static void setErrorRightGradientColor(Color errorRightGradientColor) {
		Colors.errorRightGradientColor = errorRightGradientColor;
	}
	
	public static Color getCurrentLineColor() {
		return currentLineColor;
	}

	public static void setCurrentLineColor(Color currentLineColor) {
		Colors.currentLineColor = currentLineColor;
	}


	public static Color getErrorTextColor() {
		return errorTextColor;
	}

	public static void setErrorTextColor(Color errorTextColor) {
		Colors.errorTextColor = errorTextColor;
	}

	public static Color getBackgroundColor() {
		return backgroundColor;
	}

	public static void setBackgroundColor(Color backgroundColor) {
		Colors.backgroundColor = backgroundColor;
	}

	public static Color getForegroundColor() {
		return foregroundColor;
	}

	public static void setForegroundColor(Color foregroundColor) {
		Colors.foregroundColor = foregroundColor;
	}

	public static Color getFoldToggleColor() {
		return foldToggleColor;
	}

	public static void setFoldToggleColor(Color foldToggleColor) {
		Colors.foldToggleColor = foldToggleColor;
	}

	public static Color getBreakPointColor() {
		return breakPointColor;
	}

	public static void setBreakPointColor(Color breakPointColor) {
		Colors.breakPointColor = breakPointColor;
	}

	public static Color getBookmarkColor() {
		return bookmarkColor;
	}

	public static void setBookmarkColor(Color bookmarkColor) {
		Colors.bookmarkColor = bookmarkColor;
	}

	public static Color getGutterForegroundColor() {
		return gutterForegroundColor;
	}

	public static void setGutterForegroundColor(Color gutterForegroundColor) {
		Colors.gutterForegroundColor = gutterForegroundColor;
	}

	public static Color getGutterBackgroundColor() {
		return gutterBackgroundColor;
	}

	public static void setGutterBackgroundColor(Color gutterBackgroundColor) {
		Colors.gutterBackgroundColor = gutterBackgroundColor;
	}


	
}
