
package org.libcode.fold;

import javax.swing.text.AbstractDocument;
import javax.swing.text.Element;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import org.libcode.fold.view.FoldingParagraphView;
import org.libcode.ui.CodePane;

/**
 *
 * @author hexaredecimal
 */
public class FoldingEditorKit extends StyledEditorKit {
	private final ViewFactory factory;

  public FoldingEditorKit(CodePane pane) {
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
