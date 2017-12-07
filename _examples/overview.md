---
layout: page
title: Overview
order: 10
permalink: /examples/
---

> The following pages were adapted from the original [Markdown documentation](https://daringfireball.net/projects/markdown/syntax#overview) 
> to demonstrate the appearance of this Jekyll theme.

Markdown is intended to be as easy-to-read and easy-to-write as is feasible. 

Readability, however, is emphasized above all else. A Markdown-formatted document should be publishable
as-is, as plain text, without looking like it's been marked up with tags or formatting instructions. 
While Markdown's syntax has been influenced by several existing text-to-HTML filters, the single biggest 
source of inspiration for Markdown's syntax is the format of plain text email.

To this end, Markdown's syntax is comprised entirely of punctuation characters, which punctuation 
characters have been carefully chosen so as to look like what they mean. E.g., asterisks around a 
word actually look like \*emphasis\*. Markdown lists look like, well, lists. Even blockquotes look 
like quoted passages of text, assuming you've ever used email.

## Special Characters

In HTML, there are two characters that demand special treatment: < and &. Left angle brackets are 
used to start tags; ampersands are used to denote HTML entities. If you want to use them as 
literal characters, you must escape them as entities, e.g. `&lt;` and `&amp;`

Markdown allows you to use these characters naturally, taking care of all the necessary escaping for you. 
If you use an ampersand as part of an HTML entity, it remains unchanged; otherwise it will be translated into `&amp;`

## Backslash Escapes

Markdown allows you to use backslash escapes to generate literal characters which would otherwise 
have special meaning in Markdown's formatting syntax. For example, if you wanted to surround a 
word with literal asterisks (instead of an HTML `<em>` tag), you can use backslashes before 
the asterisks, like this:

```
\\\*literal asterisks\\\*
```
