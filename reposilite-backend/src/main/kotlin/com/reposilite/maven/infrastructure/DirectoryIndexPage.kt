package com.reposilite.maven.infrastructure

import com.reposilite.shared.HtmlEscaping
import com.reposilite.storage.api.FileDetails
import com.reposilite.storage.api.FileType

internal fun createDirectoryIndexPage(basePath: String, uri: String, authenticatedFiles: List<FileDetails>): String {
    // The request URI arrives percent-encoded; the page shows the decoded path and links with the encoded one.
    val formattedUri = basePath + HtmlEscaping.decodeRequestPath(uri).removePrefix("/")
    val directoryText = HtmlEscaping.escape(formattedUri)
    val baseHref = HtmlEscaping.escape(HtmlEscaping.encodePath(formattedUri.removeSuffix("/")) + "/")
    val parentHref = HtmlEscaping.escape(HtmlEscaping.encodePath(formattedUri.removeSuffix("/").substringBeforeLast("/")))

    // language=html
    return """
        <!DOCTYPE html>
        <html lang='en'>
            <head>
                <title>Index of $directoryText</title>
                <meta charset='utf-8'>
                <base href='$baseHref'>
                <style>
                li {
                    padding: 2px 10px;
                }
                .back::marker {
                    content: '🔙';
                }
                .directory::marker {
                    content: '📁';
                }
                .file::marker {
                    content: '📄';
                }
                </style>
            </head>
            <body>
                <h1>Index of $directoryText</h1>
                <ul>
                    <li class='back'>
                        <a href='$parentHref'>Parent Directory</a>
                    </li>
                    ${authenticatedFiles.flatMap {
                        val fileSeparator = if (it.type == FileType.DIRECTORY) "/" else ""
                        val entryHref = HtmlEscaping.escape(HtmlEscaping.encodePath(it.name))
                        val entryText = HtmlEscaping.escape(it.name)

                        listOf(
                            """<li class="${it.type.name.lowercase()}">""",
                            """<a href="./$entryHref$fileSeparator">$entryText$fileSeparator</a>""",
                            """</li>"""
                        )
                    }.joinToString(separator = "")}
                </ul>
            </body>
        </html>
    """.trimIndent()
}
