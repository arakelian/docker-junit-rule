---
layout: page
title: Syntax Highlighter
order: 40
---

## Apache

```apache
AddEncoding x-gzip .gz
RewriteCond %{HTTP:Accept-Encoding} gzip
RewriteCond %{REQUEST_FILENAME}.gz -f
RewriteRule ^(.*)$ $1.gz [QSA,L]
<FilesMatch \.css\.gz$>
  ForceType text/css
  Header append Vary Accept-Encoding
</FilesMatch>
```

## C

```c
#include "ruby/ruby.h"

static int
clone_method_i(st_data_t key, st_data_t value, st_data_t data)
{
    clone_method((VALUE)data, (ID)key, (const rb_method_entry_t *)value);
    return ST_CONTINUE;
}
```

## Gradle

```gradle
apply plugin: 'java'

repositories {
  jcenter()
}

dependencies {
  compile 'org.openjdk.jmh:jmh-core:1.12'
  compile 'org.openjdk.jmh:jmh-generator-annprocess:1.12'
}
```

## Groovy

```groovy
class Greet {
  def name
  Greet(who) { name = who[0].toUpperCase() +
                      who[1..-1] }
  def salute() { println "Hello $name!" }
}

g = new Greet('world')  // create object
g.salute()              // output "Hello World!"
```

## HTML

```html
<html>
  <head><title>Title!</title></head>
  <body>
    <p id="foo">Hello, World!</p>
    <script type="text/javascript">var a = 1;</script>
    <style type="text/css">#foo { font-weight: bold; }</style>
  </body>
</html>
```

## HTTP

```http
POST /demo/submit/ HTTP/1.1
Host: rouge.jneen.net
Cache-Control: max-age=0
Origin: http://rouge.jayferd.us
User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_2)
    AppleWebKit/535.7 (KHTML, like Gecko) Chrome/16.0.912.63 Safari/535.7
Content-Type: application/json
Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
Referer: http://pygments.org/
Accept-Encoding: gzip,deflate,sdch
Accept-Language: en-US,en;q=0.8
Accept-Charset: windows-949,utf-8;q=0.7,*;q=0.3

{"name":"test","lang":"text","boring":true}
```

## .INI files

```ini
; last modified 1 April 2001 by John Doe
[owner]
name=John Doe
organization=Acme Widgets Inc.
```

## Java

```java
public class java {
    public static void main(String[] args) {
        System.out.println("Hello World");
    }
}
```

## Javascript

```javascript
// Set variables
var myBankBalance = 0;
var output = "";

// Do the 'while' loop
while (myBankBalance <= 10) {
  if (myBankBalance === 5) { 
    break; 
    }
  output += "My bank balance is now $" + myBankBalance + "<br>";
  myBankBalance ++;
}

// Output results to the above HTML element
document.getElementById("msg").innerHTML = output;
```

## Json

```json
{
  "me": {
    "name": "Luke Skywalker"
  }
}
```

## Markdown

```markdown
Markdown has cool [reference links][ref 1]
and [regular links too](http://example.com)

[ref 1]: http://example.com
```

## Objective C

```objective_c
@interface Person : NSObject {
  @public
  NSString *name;
  @private
  int age;
}

@property(copy) NSString *name;
@property(readonly) int age;

-(id)initWithAge:(int)age;
@end

NSArray *arrayLiteral = @[@"abc", @1];
NSDictionary *dictLiteral = @{
  @"hello": @"world",
  @"goodbye": @"cruel world"
};
```

## PHP

```php
<?php
  print("Hello {$world}");
?>
```

## Protobuf

```protobuf
message Person {
  required string name = 1;
  required int32 id = 2;
  optional string email = 3;
}
```

## Ruby

```ruby
class Greeter
  def initialize(name="World")
    @name = name
  end

  def say_hi
    puts "Hi #{@name}!"
  end
end
```

## Scala

```scala
class Greeter(name: String = "World") {
  def sayHi() { println("Hi " + name + "!") }
}
```

## SASS

```sass
@for $i from 1 through 3
  .item-#{$i}
    width: 2em * $i
```

## SCSS

```scss
@for $i from 1 through 3 {
  .item-#{$i} {
    width: 2em * $i;
  }
}
```

## Shell

```shell
# this while loop iterates over all lines of the file
while read LINE
do
    # increase line counter 
    ((count++))
    # write current line to a tmp file with name $file (not needed for counting)
    echo $LINE > $file
    # this checks the return code of echo (not needed for writing; just for demo)
    if [ $? -ne 0 ] 
     then echo "Error in writing to file ${file}; check its permissions!"
    fi
done
```

## SQL

```sql
SELECT * FROM `users` WHERE `user`.`id` = 1
```

## Swift

```swift
// Say hello to poeple
func sayHello(personName: String) -> String {
    let greeting = "Hello, " + personName + "!"
    return greeting
}
```

## Visual Basic

```vb
Private Sub Form_Load()
    ' Execute a simple message box that says "Hello, World!"
    MsgBox "Hello, World!"
End Sub
```

## XML

```xml
<?xml version="1.0"?>
<book id="bk101">
   <author>Gambardella, Matthew</author>
   <title>XML Developer's Guide</title>
   <genre>Computer</genre>
   <price>44.95</price>
   <publish_date>2000-10-01</publish_date>
   <description>An in-depth look at creating applications with XML.</description>
</book>
```