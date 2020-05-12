# mkproj-demo

## An empty project made by mkproj

(when in VS code, press `ctrl-k v` to render this file)

## What does this project deliver

This project shows several things:

1. Hot CSS reloading
2. Hot code reloading (compile-on-save, no browser reload required)
3. RPC mechanism for bidirectional websocket to back-end
4. sample querying of CouchDB via REST interface
5. initialized GIT repository
6. one language for client (browser) and server code
7. extremely low complexity (the Abstract Syntax Tree of the code contains just under 160 nodes!)
8. extreme small project size (±300 lines of code **and** project files, not counting README)
9. package entire project into 1 file (.jar) to run everywhere
10. backend is JVM with a web-server and server-side code
11. front-end code is compiled to javascript and delivered via JVM web-server
12. JS code size while developing: less than 6MB
13. JS code size after compiling for production: ±550KB (less than 140KB gzipped)
14. segmented state map using lenses (reduces events and facilitates refactoring)
15. paginated DB access

## how to develop

1. have a linux system ready, or find out the rest by yourself :)
2. install `clj` from http://clojure.org/
3. make sure you have git installed
4. have Visual Studio Code installed to follow this document precisely
5. run VS code and install `calva` plugin, no config required
6. open your standard browser **first**
7. open this project (obviously)
8. **then** start project with `ctrl-alt-c ctrl-alt-j`
9. select `Clojure CLI + Figwheel-main`, then no options in next dialogue  
9b. the running browser will show the app
10. change browser URL from port 9500 to 8000; nothing will show up
11. open `src/clj/mkproj-demo/core.clj` and click in window
12. press `ctrl-alt-c enter` to compile namespace
13. type in (in CLJ REPL): `(mkproj-demo.core/-main)`
14. refresh browser
15. any changes you save lead to immediate changes in the browser

## using the CouchDB demo

1. install CouchDB
2. make database named `sample`
3. create document with fields "type": "person", "name": "Jack", "age": 21
4. create index (for sorting) on "name"
5. enable CORS in settings

## compile for distribution

1. close VS code
2. in project directory run `clj -Aoptimal`
3. delete extraneous files: `rm -rf target/public/cljs-out/dev`
4. generate pom.xml with `clj -Spom`
5. package with `clj -Auberjar`
6. run with `java -cp target/mkproj-demo.jar clojure.main -m mkproj-demo.core`
7. open browser to http://localhost:8000/ and refresh if page not loading


There might be a few seconds delay to start.
If you move the jar file, keep in mind that all paths are relative to the location from where you run, i.e. if the code opening `data/items.edn` is still there, you'll get an error.

## Check for outdated dependencies

Run `clj -Aoutdated` from the project directory.
A table of suggested updates (to be put in deps.edn) might be shown.

## Study material

Lightweight intro:  
http://maria.cloud/

http://clojure.org/  
http://clojurescript.org/  
http://hoplon.io/  
https://github.com/jarohen/chord  

©2020 Roel Pompen
