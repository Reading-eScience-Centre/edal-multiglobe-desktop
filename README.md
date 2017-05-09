Multiglobe Desktop
==================

This research-focussed software has a number of innovations for visualising and querying the type of large data collections which researchers in the department use on a day-to-day basis. It enables users to view and query multiple datasets simultaneously, and allows them to smoothly and intuitively scan through the time and depth dimensions, creating dynamic animations where in the past pre-rendering would have been required.

The code is based on the [NASA World Wind SDK](http://worldwind.arc.nasa.gov/java/) as well as our own [EDAL libraries](https://github.com/Reading-eScience-Centre/edal-java) developed for [ncWMS](https://github.com/Reading-eScience-Centre/ncwms).

Key features of the code include:

* Simultaneous viewing of as many datasets as you can fit on your screen
* View data on a 3D globe or a flat map
* Synchronise the movement of different globes
* Automatically synchronise the time/depth axes where possible
* Interactive animation of data fields (varying in depth or time)
* Query data at a point and generate timeseries/vertical profile graphs
* The ability to read data in any format supported by the Unidata NetCDF-Java libraries (including NetCDF, HDF, GRIB, OpenDAP)
* Supports plotting of in-situ ocean observations from Argo floats, moorings, and buoys in the Met Office EN3 database
* Configuration of colour scales
* Can share configuration files with ncWMS - make the same data available on a video wall and over the web

To get an idea of the capabilities of the software and see some demonstrations of it in action, have a look at [this video](https://www.youtube.com/watch?v=aUWD8J6xyms).

The project is built using Maven and can be run with `mvn exec:java` or by building the JAR file and running it in the usual way.
