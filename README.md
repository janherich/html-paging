# recepty-paging

Simple clojure command line program to generate paging for html sites.

## Usage

Create uberjar file from the project with the help of leiningen uberjar task.
Then run the program for example with such invocation:
`java -jar filename.jar najnovsie-recepty '[:div#wrappers]' '[:div#wrappers :div.recept]' '[:div#container :div.pagination]' '[:div#container :div.pagination :span]' '[:div#container :div.pagination :a]' -p /home/jhe/Development/paging-test -c 2`.
Run the program with `-h` flag to see list of all available options.

## License

Copyright © 2014 Jan Herich

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
