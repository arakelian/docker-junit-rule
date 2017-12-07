---
layout: page
title: Span Elements
order: 30
---

## Links

Markdown supports two style of links: *inline* and *reference*.

To create an inline link, use a set of regular parentheses immediately after the link text’s 
closing square bracket. Inside the parentheses, put the URL where you want the link to point, 
along with an optional title for the link, surrounded in quotes. For example:

```markdown
This theme was developed by [Greg Arakelian](http://arakelian.com/ "Greg's Website") 
inline link.
```

Will produce the following markup:

This theme was developed by [Greg Arakelian](http://arakelian.com/ "Greg's Website") inline link.

## Emphasis

Markdown treats asterisks (*) and underscores (_) as indicators of emphasis. 

- To **boldface** text, you can use `**double asterisks**`.
- To __boldface__ text, you can also use `__double underlines__`.
- To *italicize* text, you can use `*single asterisks*`.
- To _italicize_ text, you can also use `_single underlines_`.

## HTML

Markdown is not a replacement for HTML, or even close to it. Its syntax is very small, corresponding only to a very small subset of HTML tags. The idea is not to create a syntax that makes it easier to insert HTML tags.

For any markup that is not covered by Markdown’s syntax, you simply use HTML itself. There’s no need to preface it or delimit it to indicate that you’re switching from Markdown to HTML; you just use the tags.

- Abbreviations, like <abbr title="HyperText Markup Langage">HTML</abbr> should use `<abbr>`, with an optional `title` attribute for the full phrase.
- Citations, like <cite>&mdash; Greg Arakelian</cite>, should use `<cite>`.
- <del>Deleted</del> text should use `<del>` and <ins>inserted</ins> text should use `<ins>`.
- Superscript <sup>text</sup> uses `<sup>` and subscript <sub>text</sub> uses `<sub>`.


## Code

To indicate a span of code, wrap it with backtick quotes (`` ` ``). Unlike a pre-formatted code block, a code span indicates code within a normal paragraph. 

For example: The C library function `sprintf()` is used to store formatted data as a string.


## Images

Markdown uses an image syntax that is intended to resemble the syntax for links, allowing for two styles: inline and reference.

Inline image syntax looks like this:

```markdown
![Alt text](/path/to/img.jpg)

![Alt text](/path/to/img.jpg "Optional title")
```

This is an example of a inline image:

![Sample alternative text](/assets/img/logo.svg "Sample title")
