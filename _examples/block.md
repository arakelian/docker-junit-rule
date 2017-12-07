---
layout: page
title: Block Elements
order: 20
---

## Paragraphs and Line Breaks

A paragraph is simply one or more consecutive lines of text, separated by one or more blank lines. (A blank 
line is any line that looks like a blank line — a line containing nothing but spaces or tabs is 
considered blank.) Normal paragraphs should not be indented with spaces or tabs.

The implication of the "one or more consecutive lines of text" rule is that Markdown supports "hard-wrapped” 
text paragraphs. This differs significantly from most other text-to-HTML formatters (including Movable 
Type's "Convert Line Breaks” option) which translate every line break character in a paragraph into a `<br>` tag.

When you do want to insert a `<br>` break tag using Markdown, you end a line with two or more spaces, then type return.

Yes, this takes a tad more effort to create a `<br>`, but a simplistic "every line break is a `<br>`” rule 
wouldn't work for Markdown. Markdown's email-style blockquoting and multi-paragraph list items work best — 
and look better — when you format them with hard breaks.

## Blockquotes

Markdown uses email-style > characters for blockquoting. If you're familiar with quoting passages of text
in an email message, then you know how to create a blockquote in Markdown.

It looks best if you hard wrap the text and put a > before every line:

> This is a blockquote with two paragraphs. Lorem ipsum dolor sit amet,
> consectetuer adipiscing elit. Aliquam hendrerit mi posuere lectus.
> Vestibulum enim wisi, viverra nec, fringilla in, laoreet vitae, risus.
> 
> Donec sit amet nisl. Aliquam semper ipsum sit amet velit. Suspendisse
> id sem consectetuer libero luctus adipiscing.


### Nested Blockquotes

Blockquotes can contain other Markdown elements, including headers, lists, and code blocks:

```markdown
> This is the first level of quoting.
>
> > This is nested blockquote.
>
> Back to the first level.
```

Produces the following:

> This is the first level of quoting.
>
> > This is nested blockquote.
>
> Back to the first level.


## Lists

Markdown supports ordered (numbered) and unordered (bulleted) lists.

Unordered lists use asterisks, pluses, and hyphens — interchangably — as list markers:

* Asterisks `*`
* Pluses `+`
* Hyphens `-`

Ordered lists use numbers followed by periods:

1.  Bird
2.  McHale
3.  Parish

It's important to note that the actual numbers you use to mark the list have no effect on the HTML output Markdown produces. 

### Multiple Paragraphs

List items may consist of multiple paragraphs. Each subsequent paragraph in a list item must be indented by either 4 spaces or one tab:

1.  This is a list item with two paragraphs. Lorem ipsum dolor
    sit amet, consectetuer adipiscing elit. Aliquam hendrerit
    mi posuere lectus.

    Vestibulum enim wisi, viverra nec, fringilla in, laoreet
    vitae, risus. Donec sit amet nisl. Aliquam semper ipsum
    sit amet velit.

2.  Suspendisse id sem consectetuer libero luctus adipiscing.


### Blockquotes 

To put a blockquote within a list item, the blockquote's > delimiters need to be indented:

*   A list item with a blockquote:

    > This is a blockquote
    > inside a list item.
    
To put a code block within a list item, the code block needs to be indented twice — 8 spaces or two tabs:

*   A list item with a code block:

        <code goes here>

## Code Blocks

Pre-formatted code blocks are used for writing about programming or markup source code. 
Rather than forming normal paragraphs, the lines of a code block are interpreted literally. 
Markdown wraps a code block in both `<pre>` and `<code>` tags.

This is a normal paragraph:

    This is a code block.

A code block continues until it reaches a line that is not indented.

Within a code block, ampersands (&) and angle brackets (< and >) are automatically converted into 
HTML entities. This makes it very easy to include example HTML source code using Markdown — 
just paste it and indent it, and Markdown will handle the hassle of encoding the ampersands 
and angle brackets. 

This text is appears exactly like this in the source file:

    <div class="footer">
        &copy; 2004 Foo Corporation
    </div>


## Horizontal Rules

You can produce a horizontal rule tag (`<hr>`) by placing three or more hyphens, asterisks, or
underscores on a line by themselves. 

---------------------------------------

If you wish, you may use spaces between the hyphens or asterisks. 

