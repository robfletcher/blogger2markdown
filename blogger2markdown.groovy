@Grab('joda-time:joda-time:2.1')
import org.joda.time.*

@Grab('org.apache.directory.studio:org.apache.commons.lang:2.6')
import static org.apache.commons.lang.StringEscapeUtils.*

assert args.length == 1, "specify filename"

def targetDir = new File('target')
targetDir.mkdirs()

def file = new File(args[0])

def entries = new XmlSlurper().parse(file).entry.findAll { entry ->
	isPost(entry) && !isDraft(entry)
}

println "found ${entries.size()} entries"
for (entry in entries) {
	def title = entry.title.text()

	def published = new DateTime(entry.published.text())

	def tags = entry.category.findAll { it.@scheme == 'http://www.blogger.com/atom/ns#' }.@term.collect { "#$it" }

	def content = markdownify(entry.content.text())

	def filename = "${published.toString('yyyy-MM-dd')}-${normalize(title)}.md"

	new File(targetDir, filename).withWriter() { writer ->
		writer << content
		writer << '\n\n'
		writer << tags.collect { "\\$it" }.join(' ')
	}
}

private String markdownify(String text) {
	text = replaceCodeBlocks text
	text = replaceParagraphs text
	text = replaceInlineElements text
	text = replaceHeaders text
	text = replaceOrderedLists text
	text = replaceUnorderedLists text
	text = replaceImages text
	text = replaceUnnecessaryMarkup text
	text = tidyBlankLines text
	text
}

private String replaceCodeBlocks(String text) {
	text.replaceAll(~/<pre>(?:<code>)?(.*?)(?:<\/code>)?<\/pre>/) { match ->
		"\n\n    ${unescapeHtml(match[1].replaceAll('<br />', '\n    '))}\n\n"
	}
}

private String replaceParagraphs(String text) {
	text = text.replaceAll ~/<\/?p>|<br \/>/, '\n'
}

private String replaceInlineElements(String text) {
	text = text.replaceAll ~/<a(?:.*?)href="(.*?)"(?:.*?)title="(.*?)"(?:.*?)>(.*?)<\/a>/, '[$3]($1 "$2")'
	text = text.replaceAll ~/<a(?:.*?)href="(.*?)"(?:.*?)>(.*?)<\/a>/, '[$2]($1)'
	text = text.replaceAll ~/<em(?:.*?)>(.*?)<\/em>/, '_$1_'
	text = text.replaceAll ~/<strong(?:.*?)>(.*?)<\/strong>/, '**$1**'
	text = text.replaceAll ~/<code(?:.*?)>(.*?)<\/code>/, '`$1`'
	text = text.replaceAll(~/<tt(?:.*?)>(.*?)<\/tt>/) { match ->
		unescapeHtml "`${match[1]}`"
	}
}

private String replaceHeaders(String text) {
	text = text.replaceAll(~/(?s)<h([1-6])(?:.*?)>(.*?)<\/h\1>/) { match ->
		"\n\n${'#' * match[1].toInteger()} ${match[2]}\n\n"
	}
}

private String replaceOrderedLists(String text) {
	text = text.replaceAll(~/(?s)<ol>(.*?)<\/ol>/) { listMatch ->
		int i = 1
		'\n\n' + listMatch[1].replaceAll(~/(?s)<li>(.*?)<\/li>/) { itemMatch ->
			"${i++}. ${itemMatch[1]}\n"
		} + '\n\n'
	}
}

private String replaceUnorderedLists(String text) {
	text = text.replaceAll(~/(?s)<ul>(.*?)<\/ul>/) { listMatch ->
		'\n\n' + listMatch[1].replaceAll(~/(?s)<li>(.*?)<\/li>/) { itemMatch ->
			"* ${itemMatch[1]}\n"
		} + '\n\n'
	}
}

private String replaceImages(String text) {
	text = text.replaceAll ~/<img(?:.*?)src="(.*?)"(?:.*?)alt="(.*?)"(?:.*?)title="(.*?)"(?:.*?)>/, '![$2]($1 $3)'
	text = text.replaceAll ~/<img(?:.*?)src="(.*?)"(?:.*?)alt="(.*?)"(?:.*?)>/, '![$2]($1)'
	text = text.replaceAll ~/<img(?:.*?)src="(.*?)"(?:.*?)>/, '![]($1)'
}

private String replaceUnnecessaryMarkup(String text) {
	text = text.replaceAll(~/(?s)<a name=".*?">(.*?)<\/a>/) { match ->
		match[1].trim()
	}
	text = text.replaceAll ~/<\/>/, ''
	text = text.replaceAll ~/<div(?:.*?)>/, ''
}

private String tidyBlankLines(String text) {
	text = text.replaceAll ~/(?m)^(\s*\n){2,}/, '\n'
}

private String normalize(String s) {
	s.toLowerCase().replaceAll(/\s+/, '-').replaceAll(/&/, 'and').replaceAll(/[^\w-]+/, '')
}

private boolean isPost(node) {
	node.category.find {
		it.@scheme == 'http://schemas.google.com/g/2005#kind' && it.@term == 'http://schemas.google.com/blogger/2008/kind#post'
	}
}

private boolean isDraft(node) {
	node.control.draft.text() == 'yes'
}