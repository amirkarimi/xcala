#Xcala#

This is a web application library based on Scala, Play and ReactiveMongo.

##Parts##
Xcala provides helpers for various parts of web development.

###Controllers (xcala.play.controllers)###
Provides base functionality for CRUD operations, support server side pagination, data criterias, localization, etc.

###Views (views.html.xcala.play)###
Provides base components for rendering HTML pages, like:

* Bootstrap input renderes
* Master-detail input structures
* MVC-freindly grid view with sort and pagination
* etc.

###Services (xcala.play.services)###
Provides base functionality for building CRUD data services, server side pagination, data criterias, GridFS, tree structures, decorators, etc.

###Extensions (xcala.play.extensions)###
Extending existing classes and objects:

* BSON helpers like `DateTime`, `BigDecimal`, `Range`, etc BSON handlers
* Play `Form` helpers for `BSONObjectID` and other things
* Other helpers

###Models (xcala.play.models)###
Models used in other part of Xcala.

###Utils###
Other useful utilities.

#Build and Use#

##Build##

```
activator compile
```

##Use##

####Publish locally:####

```
activator publish-local
```

####Use in other projects:####

```
libraryDependencies += "com.xcala" %% "xcala-play" % "0.3"
```

>I haven't published Xcala into maven repositories yet. Because it's still under development and it's easier for me to use `publish-local`. Please let me know if there are simpler ways ;)

#Known Issues#
* Project structure is like a Play application while it's a library and should be improved
* Data services are [hardly-coupled to Play config](https://github.com/AmirKarimi/xcala/blob/22de6022ca21612ff065ce58a6fa1e39debb24b4/src/app/xcala.play/services/DatabaseConfig.scala#L36) which should be decoupled