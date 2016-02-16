# repl-utils

Some things I made to help my Clojure repl and you can too.

## Obtention

```
[com.gfredericks/repl-utils "0.2.7"]
```

## The Good Parts

### `bg`

`bg` is a macro that is similar to `future` but with lots of
repl-friendly features.

First a brief example:

``` clojure
user> (require '[com.gfredericks.repl.bg :refer [bg]])
nil
user> (bg (Thread/sleep 2500) (inc 41))
Starting background task bg0
#<bg0 has been running for 0.003 seconds>
user> #<DONE: bg0 ran for 2.503 seconds>

user> bg0
42
user> (bg (/ 42 0))
Starting background task bg1
#<bg1 has been running for 0.000 seconds>#<ERROR(Divide by zero): bg1 ran for 0.000 seconds
user> >

user> bg1
#<java.lang.ArithmeticException@69b032 java.lang.ArithmeticException: Divide by zero>
```

The main idea is that `bg` is based on vars while `future` returns an `IDeref`. This means
that instead of typing

``` clojure
user> (def f (future (inc 41)))
#'user/f
user> @f
42
```

you can type

``` clojure
user> (bg (inc 41))
Starting background task bg2
#<bg2 has been running for 0.001 seconds>#<DONE: bg2 ran for 0.001 seconds>
user> bg2
42
```

I.e., you get a name for free, and you don't have to use `@`/`deref`.

#### Minor Features

- If an exception is thrown, the var will hold the exception instead of
  the result.
- The var itself (i.e., the result of evaluating `#'bg1`) will print with
  state and timing info.
- A message will be printed to `*out*` when the task finishes/errors.
- You can access an actual future via `(-> #'bg1 meta :future)` in case
  you want to block on the result or whatever.
- You can get the original form via `(-> #'bg1 meta :form)` in case you
  lose track of what you originally evaluated or something.
- You can give the var a custom name via `(bg some-name ...)` if you
  find that useful.

### `mexpand-all`

A variant of `clojure.walk/macroexpand-all` that has fewer bugs and
does not fully qualify symbols when possible (for the sake of better
readability).

``` clojure
user> (require '[com.gfredericks.repl.mexpand :refer [mexpand-all]])
nil
user> (mexpand-all '(fn [[x]] (identity x)))
(fn*
 ([p__21032]
  (let*
   [vec__21034 p__21032 x (clojure.lang.RT/nth vec__21034 0 nil)]
   (identity x))))
```

## License

Copyright © 2013 Gary Fredericks

Distributed under the Eclipse Public License, the same as Clojure.
