package com.craigburke.document.builder

import static com.craigburke.document.core.UnitUtil.pointToEigthPoint
import static com.craigburke.document.core.UnitUtil.pointToEmu
import static com.craigburke.document.core.UnitUtil.pointToTwip
import static com.craigburke.document.core.UnitUtil.pointToHalfPoint

import com.craigburke.document.core.HeaderFooterOptions
import com.craigburke.document.core.builder.RenderState
import com.craigburke.document.core.BlockNode
import com.craigburke.document.core.Cell
import com.craigburke.document.core.Row
import com.craigburke.document.core.Font
import com.craigburke.document.core.Image
import com.craigburke.document.core.LineBreak
import com.craigburke.document.core.PageBreak
import com.craigburke.document.core.TextBlock
import com.craigburke.document.core.Table
import com.craigburke.document.core.Text
import groovy.transform.InheritConstructors

import com.craigburke.document.core.builder.DocumentBuilder
import com.craigburke.document.core.Document

/**
 * Builder for Word documents
 * @author Craig Burke
 */
@InheritConstructors
class WordDocumentBuilder extends DocumentBuilder {

	private static final String PAGE_NUMBER_PLACEHOLDER = '##pageNumber##'
	private static final Map RUN_TEXT_OPTIONS = ['xml:space':'preserve']

	void initializeDocument(Document document, OutputStream out) {
		document.item = new WordDocument(out)
	}

	WordDocument getWordDocument() {
		document.item
	}

	void writeDocument(Document document, OutputStream out) {
		def headerFooterOptions = new HeaderFooterOptions(
				pageNumber:PAGE_NUMBER_PLACEHOLDER,
				pageCount:document.pageCount,
				dateGenerated:new Date()
		)

		def header = renderHeader(headerFooterOptions)
		def footer = renderFooter(headerFooterOptions)

		renderState = RenderState.PAGE
		wordDocument.generateDocument { builder ->
			w.document {
				w.body {
					document.children.each { child ->
						if (child instanceof TextBlock) {
							addParagraph(builder, child)
						}
						else if (child instanceof PageBreak) {
							addPageBreak(builder)
						}
						else if (child instanceof Table) {
							addTable(builder, child)
						}
					}
					w.sectPr {
						w.pgMar('w:bottom':pointToTwip(document.margin.bottom),
								'w:top':pointToTwip(document.margin.top),
								'w:right':pointToTwip(document.margin.right),
								'w:left':pointToTwip(document.margin.left),
								'w:footer':pointToTwip(footer ? footer.node.margin.bottom : 0),
								'w:header':0
						)
						if (header) {
							w.headerReference('r:id':header.id, 'w:type':'default')
						}
						if (footer) {
							w.footerReference('r:id':footer.id, 'w:type':'default')
						}
					}
				}
			}
		}

		document.item.write()
	}

	def renderHeader(HeaderFooterOptions options) {
		def header = [:]
		if (document.header) {
			renderState = RenderState.HEADER
			header.node = document.header(options)
			header.id = wordDocument.generateHeader { builder ->
				w.hdr {
					renderHeaderFooterNode(builder, header.node as BlockNode)
				}
			}
		}
		header
	}

	def renderFooter(HeaderFooterOptions options) {
		def footer = [:]
		if (document.footer) {
			renderState = RenderState.FOOTER
			footer.node = document.footer(options)
			footer.id = wordDocument.generateFooter { builder ->
				w.hdr {
					renderHeaderFooterNode(builder, footer.node as BlockNode)
				}
			}
		}
		footer
	}

	void renderHeaderFooterNode(builder, BlockNode node) {
		if (node instanceof TextBlock) {
			addParagraph(builder, node)
		}
		else {
			addTable(builder, node)
		}

	}

	void addPageBreak(builder) {
		builder.w.p {
			w.r {
				w.br('w:type':'page')
			}
		}
	}

	int calculateSpacingAfter(BlockNode node) {
		int totalSpacing = node.margin.bottom

		def items = node.parent.children
		int index = items.findIndexOf { it == node }

		if (index != items.size() - 1) {
			def nextSibling = items[index + 1]
			if (nextSibling instanceof BlockNode) {
				totalSpacing += nextSibling.margin.top
			}
		}
		totalSpacing
	}

	void addParagraph(builder, TextBlock paragraph) {
		builder.w.p {
			w.pPr {
				String lineRule = (paragraph.lineHeight) ? 'exact' : 'auto'
				BigDecimal lineValue = (paragraph.lineHeight) ?
						pointToTwip(paragraph.lineHeight) : (paragraph.textHeightMultiplier * 240)

				w.spacing(
						'w:before':pointToTwip(paragraph.margin.top),
						'w:after':pointToTwip(calculateSpacingAfter(paragraph)),
						'w:lineRule':lineRule,
						'w:line':lineValue
				)
				w.ind(
						'w:left':pointToTwip(paragraph.margin.left),
						'w:right':pointToTwip(paragraph.margin.right)
				)
				w.jc('w:val':paragraph.align.value)
			}
			paragraph.children.each { child ->
				switch (child.getClass()) {
					case Text:
						addTextRun(builder, child.font as Font, child.value as String)
						break
					case Image:
						addImageRun(builder, child)
						break
					case LineBreak:
						addLineBreakRun(builder)
						break
				}
			}
		}
	}

	void addLineBreakRun(builder) {
		builder.w.r {
			w.cr()
		}
	}

	DocumentPartType getCurrentDocumentPart() {
		switch (renderState) {
			case RenderState.PAGE:
				DocumentPartType.DOCUMENT
				break
			case RenderState.HEADER:
				DocumentPartType.HEADER
				break
			case RenderState.FOOTER:
				DocumentPartType.FOOTER
				break
		}
	}

	void addImageRun(builder, Image image) {
		String blipId = document.item.addImage(image.name, image.data, currentDocumentPart)

		int widthInEmu = pointToEmu(image.width)
		int heightInEmu = pointToEmu(image.height)
		String imageDescription = "Image: ${image.name}"

		builder.w.r {
			w.drawing {
				wp.inline(distT:0, distR:0, distB:0, distL:0) {
					wp.extent(cx:widthInEmu, cy:heightInEmu)
					wp.docPr(id:1, name:imageDescription, descr:image.name)
					a.graphic {
						a.graphicData(uri:'http://schemas.openxmlformats.org/drawingml/2006/picture') {
							pic.pic {
								pic.nvPicPr {
									pic.cNvPr(id:0, name:imageDescription, descr:image.name)
									pic.cNvPicPr {
										a.picLocks(noChangeAspect:'true')
									}
								}
								pic.blipFill {
									a.blip('r:embed':blipId)
									a.stretch {
										a.fillRect()
									}
								}
								pic.spPr {
									a.xfrm {
										a.off(x:0, y:0)
										a.ext(cx:widthInEmu, cy:heightInEmu)
									}
									a.prstGeom(prst:'rect') {
										a.avLst()
									}
								}
							}
						}
					}
				}
			}
		}
	}

	void addTable(builder, Table table) {
		builder.w.tbl {
			w.tblPr {
				w.tblW('w:w':pointToTwip(table.width))
				w.tblBorders {
					def properties = ['top', 'right', 'bottom', 'left', 'insideH', 'insideV']
					properties.each { String property ->
						w."${property}"(
							'w:sz':pointToEigthPoint(table.border.size),
							'w:color':table.border.color.hex,
							'w:val':(table.border.size == 0 ? 'none' : 'single')
						)
					}
				}
			}

			table.children.each { Row row ->
				w.tr {
					row.children.each { Cell cell ->
						w.tc {
							w.tcPr {
								w.tcW('w:w':pointToTwip(cell.width - (table.padding * 2)))
								w.tcMar {
									w.top('w:w':pointToTwip(table.padding))
									w.bottom('w:w':pointToTwip(table.padding))
									w.left('w:w':pointToTwip(table.padding))
									w.right('w:w':pointToTwip(table.padding))
								}
							}
							cell.children.each { addParagraph(builder, it) }
						}
					}
				}
			}
		}
	}

	void addTextRun(builder, Font font, String text) {
		builder.w.r {
			w.rPr {
				w.rFonts('w:ascii':font.family)
				if (font.bold) {
					w.b()
				}
				if (font.italic) {
					w.i()
				}
				w.color('w:val':font.color.hex)
				w.sz('w:val':pointToHalfPoint(font.size))
			}
			if (renderState == RenderState.PAGE) {
				w.t(text, RUN_TEXT_OPTIONS)
			}
			else {
				parseHeaderFooterText(builder, text)
			}
		}
	}

	void parseHeaderFooterText(builder, String text) {
		def textParts = text.split(PAGE_NUMBER_PLACEHOLDER)
		textParts.eachWithIndex { String part, int index ->
			if (index != 0) {
				builder.w.pgNum()
			}
			builder.w.t(part, RUN_TEXT_OPTIONS)
		}
	}

}
