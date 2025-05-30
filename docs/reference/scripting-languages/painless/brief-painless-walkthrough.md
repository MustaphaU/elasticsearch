---
mapped_pages:
  - https://www.elastic.co/guide/en/elasticsearch/painless/current/painless-walkthrough.html
products:
  - id: painless
---

# A brief painless walkthrough [painless-walkthrough]

To illustrate how Painless works, let’s load some hockey stats into an Elasticsearch index:

```console
PUT hockey/_bulk?refresh
{"index":{"_id":1}}
{"first":"johnny","last":"gaudreau","goals":[9,27,1],"assists":[17,46,0],"gp":[26,82,1],"born":"1993/08/13"}
{"index":{"_id":2}}
{"first":"sean","last":"monohan","goals":[7,54,26],"assists":[11,26,13],"gp":[26,82,82],"born":"1994/10/12"}
{"index":{"_id":3}}
{"first":"jiri","last":"hudler","goals":[5,34,36],"assists":[11,62,42],"gp":[24,80,79],"born":"1984/01/04"}
{"index":{"_id":4}}
{"first":"micheal","last":"frolik","goals":[4,6,15],"assists":[8,23,15],"gp":[26,82,82],"born":"1988/02/17"}
{"index":{"_id":5}}
{"first":"sam","last":"bennett","goals":[5,0,0],"assists":[8,1,0],"gp":[26,1,0],"born":"1996/06/20"}
{"index":{"_id":6}}
{"first":"dennis","last":"wideman","goals":[0,26,15],"assists":[11,30,24],"gp":[26,81,82],"born":"1983/03/20"}
{"index":{"_id":7}}
{"first":"david","last":"jones","goals":[7,19,5],"assists":[3,17,4],"gp":[26,45,34],"born":"1984/08/10"}
{"index":{"_id":8}}
{"first":"tj","last":"brodie","goals":[2,14,7],"assists":[8,42,30],"gp":[26,82,82],"born":"1990/06/07"}
{"index":{"_id":39}}
{"first":"mark","last":"giordano","goals":[6,30,15],"assists":[3,30,24],"gp":[26,60,63],"born":"1983/10/03"}
{"index":{"_id":10}}
{"first":"mikael","last":"backlund","goals":[3,15,13],"assists":[6,24,18],"gp":[26,82,82],"born":"1989/03/17"}
{"index":{"_id":11}}
{"first":"joe","last":"colborne","goals":[3,18,13],"assists":[6,20,24],"gp":[26,67,82],"born":"1990/01/30"}
```


## Accessing Doc Values from Painless [_accessing_doc_values_from_painless]

Document values can be accessed from a `Map` named `doc`.

For example, the following script calculates a player’s total goals. This example uses a strongly typed `int` and a `for` loop.

```console
GET hockey/_search
{
  "query": {
    "function_score": {
      "script_score": {
        "script": {
          "lang": "painless",
          "source": """
            int total = 0;
            for (int i = 0; i < doc['goals'].length; ++i) {
              total += doc['goals'][i];
            }
            return total;
          """
        }
      }
    }
  }
}
```

Alternatively, you could do the same thing using a script field instead of a function score:

```console
GET hockey/_search
{
  "query": {
    "match_all": {}
  },
  "script_fields": {
    "total_goals": {
      "script": {
        "lang": "painless",
        "source": """
          int total = 0;
          for (int i = 0; i < doc['goals'].length; ++i) {
            total += doc['goals'][i];
          }
          return total;
        """
      }
    }
  }
}
```

The following example uses a Painless script to sort the players by their combined first and last names. The names are accessed using `doc['first'].value` and `doc['last'].value`.

```console
GET hockey/_search
{
  "query": {
    "match_all": {}
  },
  "sort": {
    "_script": {
      "type": "string",
      "order": "asc",
      "script": {
        "lang": "painless",
        "source": "doc['first.keyword'].value + ' ' + doc['last.keyword'].value"
      }
    }
  }
}
```


## Missing keys [_missing_keys]

`doc['myfield'].value` throws an exception if the field is missing in a document.

For more dynamic index mappings, you may consider writing a catch equation

```
if (!doc.containsKey('myfield') || doc['myfield'].empty) { return "unavailable" } else { return doc['myfield'].value }
```


## Missing values [_missing_values]

To check if a document is missing a value, you can call `doc['myfield'].size() == 0`.


## Updating Fields with Painless [_updating_fields_with_painless]

You can also easily update fields. You access the original source for a field as `ctx._source.<field-name>`.

First, let’s look at the source data for a player by submitting the following request:

```console
GET hockey/_search
{
  "query": {
    "term": {
      "_id": 1
    }
  }
}
```

To change player 1’s last name to `hockey`, simply set `ctx._source.last` to the new value:

```console
POST hockey/_update/1
{
  "script": {
    "lang": "painless",
    "source": "ctx._source.last = params.last",
    "params": {
      "last": "hockey"
    }
  }
}
```

You can also add fields to a document. For example, this script adds a new field that contains the player’s nickname,  *hockey*.

```console
POST hockey/_update/1
{
  "script": {
    "lang": "painless",
    "source": """
      ctx._source.last = params.last;
      ctx._source.nick = params.nick
    """,
    "params": {
      "last": "gaudreau",
      "nick": "hockey"
    }
  }
}
```


## Dates [modules-scripting-painless-dates]

Date fields are exposed as `ZonedDateTime`, so they support methods like `getYear`, `getDayOfWeek` or e.g. getting milliseconds since epoch with `getMillis`. To use these in a script, leave out the `get` prefix and continue with lowercasing the rest of the method name. For example, the following returns every hockey player’s birth year:

```console
GET hockey/_search
{
  "script_fields": {
    "birth_year": {
      "script": {
        "source": "doc.born.value.year"
      }
    }
  }
}
```


## Regular expressions [modules-scripting-painless-regex]

::::{note}
Regexes are enabled by default as the Setting `script.painless.regex.enabled` has a new option, `limited`, the default. This defaults to using regular expressions but limiting the complexity of the regular expressions. Innocuous looking regexes can have staggering performance and stack depth behavior. But still, they remain an amazingly powerful tool. In addition, to `limited`, the setting can be set to `true`, as before, which enables regular expressions without limiting them.To enable them yourself set `script.painless.regex.enabled: true` in `elasticsearch.yml`.
::::


Painless’s native support for regular expressions has syntax constructs:

* `/pattern/`: Pattern literals create patterns. This is the only way to create a pattern in painless. The pattern inside the `/’s are just [Java regular expressions](https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.md). See [Pattern flags](/reference/scripting-languages/painless/painless-regexes.md#pattern-flags) for more.
* `=~`: The find operator return a `boolean`, `true` if a subsequence of the text matches, `false` otherwise.
* `==~`: The match operator returns a `boolean`, `true` if the text matches, `false` if it doesn’t.

Using the find operator (`=~`) you can update all hockey players with "b" in their last name:

```console
POST hockey/_update_by_query
{
  "script": {
    "lang": "painless",
    "source": """
      if (ctx._source.last =~ /b/) {
        ctx._source.last += "matched";
      } else {
        ctx.op = "noop";
      }
    """
  }
}
```

Using the match operator (`==~`) you can update all the hockey players whose names start with a consonant and end with a vowel:

```console
POST hockey/_update_by_query
{
  "script": {
    "lang": "painless",
    "source": """
      if (ctx._source.last ==~ /[^aeiou].*[aeiou]/) {
        ctx._source.last += "matched";
      } else {
        ctx.op = "noop";
      }
    """
  }
}
```

You can use the `Pattern.matcher` directly to get a `Matcher` instance and remove all of the vowels in all of their last names:

```console
POST hockey/_update_by_query
{
  "script": {
    "lang": "painless",
    "source": "ctx._source.last = /[aeiou]/.matcher(ctx._source.last).replaceAll('')"
  }
}
```

`Matcher.replaceAll` is just a call to Java’s `Matcher`'s [replaceAll](https://docs.oracle.com/javase/8/docs/api/java/util/regex/Matcher.md#replaceAll-java.lang.String-) method so it supports `$1` and `\1` for replacements:

```console
POST hockey/_update_by_query
{
  "script": {
    "lang": "painless",
    "source": "ctx._source.last = /n([aeiou])/.matcher(ctx._source.last).replaceAll('$1')"
  }
}
```

If you need more control over replacements you can call `replaceAll` on a `CharSequence` with a `Function<Matcher, String>` that builds the replacement. This does not support `$1` or `\1` to access replacements because you already have a reference to the matcher and can get them with `m.group(1)`.

::::{important}
Calling `Matcher.find` inside of the function that builds the replacement is rude and will likely break the replacement process.
::::


This will make all of the vowels in the hockey player’s last names upper case:

```console
POST hockey/_update_by_query
{
  "script": {
    "lang": "painless",
    "source": """
      ctx._source.last = ctx._source.last.replaceAll(/[aeiou]/, m ->
        m.group().toUpperCase(Locale.ROOT))
    """
  }
}
```

Or you can use the `CharSequence.replaceFirst` to make the first vowel in their last names upper case:

```console
POST hockey/_update_by_query
{
  "script": {
    "lang": "painless",
    "source": """
      ctx._source.last = ctx._source.last.replaceFirst(/[aeiou]/, m ->
        m.group().toUpperCase(Locale.ROOT))
    """
  }
}
```

Note: all of the `_update_by_query` examples above could really do with a `query` to limit the data that they pull back. While you **could** use a [script query](/reference/query-languages/query-dsl/query-dsl-script-query.md) it wouldn’t be as efficient as using any other query because script queries aren’t able to use the inverted index to limit the documents that they have to check.

