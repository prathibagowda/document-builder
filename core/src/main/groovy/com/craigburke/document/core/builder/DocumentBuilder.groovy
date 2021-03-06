package com.craigburke.document.core.builder

import com.craigburke.document.core.BlockNode
import com.craigburke.document.core.EmbeddedFont
import com.craigburke.document.core.Heading
import com.craigburke.document.core.Margin
import com.craigburke.document.core.StyledNode
import com.craigburke.document.core.UnitCategory

import com.craigburke.document.core.factory.CreateFactory
import com.craigburke.document.core.factory.DocumentFactory
import com.craigburke.document.core.factory.HeadingFactory
import com.craigburke.document.core.factory.PageBreakFactory
import com.craigburke.document.core.factory.ParagraphFactory
import com.craigburke.document.core.factory.LineBreakFactory
import com.craigburke.document.core.factory.ImageFactory
import com.craigburke.document.core.factory.TextFactory
import com.craigburke.document.core.factory.TableFactory
import com.craigburke.document.core.factory.RowFactory
import com.craigburke.document.core.factory.CellFactory

import com.craigburke.document.core.Document
import com.craigburke.document.core.Font

/**
 * Document Builder base class
 * @author Craig Burke
 */
abstract class DocumentBuilder extends FactoryBuilderSupport implements TextBlockBuilder, TableBuilder {

	Document document
	OutputStream out
	RenderState renderState = RenderState.PAGE

	DocumentBuilder(OutputStream out) {
		super(true)
		this.out = out
	}

    DocumentBuilder(File file) {
        super(true)
        this.out = new FileOutputStream(file)
    }

	Font getFont() {
		current.font
	}

	def invokeMethod(String name, args) {
		use(UnitCategory) {
			super.invokeMethod(name, args)
		}
	}

	void setStyles(StyledNode node, Map attributes, String nodeKey) {
		node.font = (node instanceof Document) ? new Font() : node.parent.font.clone()
		node.font.size = (node instanceof Heading) ? null : node.font.size
		String[] keys = [nodeKey]
		keys += getStyleKeys(nodeKey, node)

		if (node instanceof BlockNode) {
			Margin defaultMargin = node.getClass().DEFAULT_MARGIN
			node.margin.setDefaults(defaultMargin)
		}

		keys.each { String key ->
			Map font = (document.template && document.template.containsKey(key)) ? document.template[key].font : [:]
			node.font << font
			if (node instanceof BlockNode) {
				Map margin = (document.template && document.template.containsKey(key)) ? document.template[key].margin : [:]
				node.margin << margin
			}
		}
		node.font << attributes.font
		if (node instanceof BlockNode) {
			node.margin << attributes.margin
		}

		if (node instanceof Heading && !node.font.size) {
			node.font.size = document.font.size * Heading.FONT_SIZE_MULTIPLIERS[node.level - 1]
		}

	}

	 String[] getStyleKeys(String nodeKey, StyledNode node) {
		def keys = []
		if (node instanceof Heading) {
			keys << "heading${node.level}"
		}
		if (node.style) {
			keys << "${nodeKey}.${node.style}"
			if (node instanceof Heading) {
				keys << "heading${node.level}.${node.style}"
			}
		}
		keys
	}

    void addFont(Map params, String location) {
        EmbeddedFont embeddedFont = new EmbeddedFont(params)
        embeddedFont.file = new File(location)
        addFont(embeddedFont)
    }

    void addFont(EmbeddedFont embeddedFont) {
        document.embeddedFonts << embeddedFont
    }

	def addPageBreakToDocument
    abstract void initializeDocument(Document document, OutputStream out)
	abstract void writeDocument(Document document, OutputStream out)

	def registerObjectFactories() {
		registerFactory('create', new CreateFactory())
		registerFactory('document', new DocumentFactory())
		registerFactory('pageBreak', new PageBreakFactory())
		registerFactory('paragraph', new ParagraphFactory())
		registerFactory('lineBreak', new LineBreakFactory())
		registerFactory('image', new ImageFactory())
		registerFactory('text', new TextFactory())
		registerFactory('table', new TableFactory())
		registerFactory('row', new RowFactory())
		registerFactory('cell', new CellFactory())
		registerFactory('heading1', new HeadingFactory())
		registerFactory('heading2', new HeadingFactory())
		registerFactory('heading3', new HeadingFactory())
		registerFactory('heading4', new HeadingFactory())
		registerFactory('heading5', new HeadingFactory())
		registerFactory('heading6', new HeadingFactory())
	}
}

enum RenderState {
	PAGE, HEADER, FOOTER
}
