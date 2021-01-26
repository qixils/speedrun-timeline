# speedrun-timeline
This is a data visualization program that displays the progression of speedrun world records over time.
It is composed of two parts: the [data collector](https://github.com/lexikiq/speedrun-timeline/blob/master/scripts/src-collector.py), which collects data from speedrun.com leaderboards and downloads profile pictures,
and the renderer, written in Java using the [Processing](https://processing.org/) graphics library.
The renderer comes in two forms: the [main development files](https://github.com/lexikiq/speedrun-timeline/tree/master/src/main/java/io/github/lexikiq/vistest) and [Processing sketch files](https://github.com/lexikiq/speedrun-timeline/tree/master/VisApplet).
The latter is used for actually saving video files and is manually "compiled" from time to time (I basically just need to reformat the file and uncomment some lines).
This project can be opened in IntelliJ for a proper IDE experience, or the sketch can be opened in Processing for a more basic experience.

This software was inspired by and uses small excerpts from [carykh's abacaba tutorial](https://github.com/carykh/AbacabaTutorialDrawer), [licensed under the MIT license](https://github.com/carykh/AbacabaTutorialDrawer/blob/main/LICENSE).

## Examples

My videos created using this software can be found [here](https://www.youtube.com/playlist?list=PLihfaHwbu8e9fNIjseGlWEegEP2TRepOn).

## License

This project is licensed under the MIT license.
The full text can be found [here](https://github.com/lexikiq/speedrun-timeline/blob/master/LICENSE).
My only request is that you send me any videos you make with this, I wanna see how people use it :)
Tag me on [twitter](https://twitter.com/lexikiq) if you do!
(Or don't. This isn't legally binding.)
