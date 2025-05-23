---
navigation_title: "Cumulative cardinality"
mapped_pages:
  - https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-pipeline-cumulative-cardinality-aggregation.html
---

# Cumulative cardinality aggregation [search-aggregations-pipeline-cumulative-cardinality-aggregation]


A parent pipeline aggregation which calculates the Cumulative Cardinality in a parent histogram (or date_histogram) aggregation. The specified metric must be a cardinality aggregation and the enclosing histogram must have `min_doc_count` set to `0` (default for `histogram` aggregations).

The `cumulative_cardinality` agg is useful for finding "total new items", like the number of new visitors to your website each day. A regular cardinality aggregation will tell you how many unique visitors came each day, but doesn’t differentiate between "new" or "repeat" visitors. The Cumulative Cardinality aggregation can be used to determine how many of each day’s unique visitors are "new".

## Syntax [_syntax_12]

A `cumulative_cardinality` aggregation looks like this in isolation:

```js
{
  "cumulative_cardinality": {
    "buckets_path": "my_cardinality_agg"
  }
}
```

$$$cumulative-cardinality-params$$$

| Parameter Name | Description | Required | Default Value |
| --- | --- | --- | --- |
| `buckets_path` | The path to the cardinality aggregation we wish to find the cumulative cardinality for (see [`buckets_path` Syntax](/reference/aggregations/pipeline.md#buckets-path-syntax) for more details) | Required |  |
| `format` | [DecimalFormat pattern](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/text/DecimalFormat.html) for theoutput value. If specified, the formatted value is returned in the aggregation’s`value_as_string` property | Optional | `null` |

The following snippet calculates the cumulative cardinality of the total daily `users`:

```console
GET /user_hits/_search
{
  "size": 0,
  "aggs": {
    "users_per_day": {
      "date_histogram": {
        "field": "timestamp",
        "calendar_interval": "day"
      },
      "aggs": {
        "distinct_users": {
          "cardinality": {
            "field": "user_id"
          }
        },
        "total_new_users": {
          "cumulative_cardinality": {
            "buckets_path": "distinct_users" <1>
          }
        }
      }
    }
  }
}
```

1. `buckets_path` instructs this aggregation to use the output of the `distinct_users` aggregation for the cumulative cardinality


And the following may be the response:

```console-result
{
   "took": 11,
   "timed_out": false,
   "_shards": ...,
   "hits": ...,
   "aggregations": {
      "users_per_day": {
         "buckets": [
            {
               "key_as_string": "2019-01-01T00:00:00.000Z",
               "key": 1546300800000,
               "doc_count": 2,
               "distinct_users": {
                  "value": 2
               },
               "total_new_users": {
                  "value": 2
               }
            },
            {
               "key_as_string": "2019-01-02T00:00:00.000Z",
               "key": 1546387200000,
               "doc_count": 2,
               "distinct_users": {
                  "value": 2
               },
               "total_new_users": {
                  "value": 3
               }
            },
            {
               "key_as_string": "2019-01-03T00:00:00.000Z",
               "key": 1546473600000,
               "doc_count": 3,
               "distinct_users": {
                  "value": 3
               },
               "total_new_users": {
                  "value": 4
               }
            }
         ]
      }
   }
}
```

Note how the second day, `2019-01-02`, has two distinct users but the `total_new_users` metric generated by the cumulative pipeline agg only increments to three. This means that only one of the two users that day were new, the other had already been seen in the previous day. This happens again on the third day, where only one of three users is completely new.


## Incremental cumulative cardinality [_incremental_cumulative_cardinality]

The `cumulative_cardinality` agg will show you the total, distinct count since the beginning of the time period being queried. Sometimes, however, it is useful to see the "incremental" count. Meaning, how many new users are added each day, rather than the total cumulative count.

This can be accomplished by adding a `derivative` aggregation to our query:

```console
GET /user_hits/_search
{
  "size": 0,
  "aggs": {
    "users_per_day": {
      "date_histogram": {
        "field": "timestamp",
        "calendar_interval": "day"
      },
      "aggs": {
        "distinct_users": {
          "cardinality": {
            "field": "user_id"
          }
        },
        "total_new_users": {
          "cumulative_cardinality": {
            "buckets_path": "distinct_users"
          }
        },
        "incremental_new_users": {
          "derivative": {
            "buckets_path": "total_new_users"
          }
        }
      }
    }
  }
}
```

And the following may be the response:

```console-result
{
   "took": 11,
   "timed_out": false,
   "_shards": ...,
   "hits": ...,
   "aggregations": {
      "users_per_day": {
         "buckets": [
            {
               "key_as_string": "2019-01-01T00:00:00.000Z",
               "key": 1546300800000,
               "doc_count": 2,
               "distinct_users": {
                  "value": 2
               },
               "total_new_users": {
                  "value": 2
               }
            },
            {
               "key_as_string": "2019-01-02T00:00:00.000Z",
               "key": 1546387200000,
               "doc_count": 2,
               "distinct_users": {
                  "value": 2
               },
               "total_new_users": {
                  "value": 3
               },
               "incremental_new_users": {
                  "value": 1.0
               }
            },
            {
               "key_as_string": "2019-01-03T00:00:00.000Z",
               "key": 1546473600000,
               "doc_count": 3,
               "distinct_users": {
                  "value": 3
               },
               "total_new_users": {
                  "value": 4
               },
               "incremental_new_users": {
                  "value": 1.0
               }
            }
         ]
      }
   }
}
```


