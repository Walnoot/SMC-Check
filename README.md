# UrPal -- Uppaal SMC Version
Applying sanity checks to find commonly made errors in Uppaal SMC models. This repository is a rewrite of the [Urpal](https://github.com/utwente-fmt/UrPal) plugin, and aims to facilitate model validation for Uppaal SMC models (whereas the Urpal plugin only supports 'pure' Uppaal timed automata models). New checks aimed specifically at Uppaal SMC models are added, and a subset of Urpal checks are adapted to work with Uppaal SMC models.

### Setup
##### Repository initialization
Clone the repository with the ```--recurse-submodules``` argument in order to automatically initialize and update each submodule in the repository (recommended).
Or execute ```git submodule update --init --recursive``` in the repository after cloning normally to achieve the same.
##### Environment variables
Set an environment variable ```UPPAAL_ROOT``` to the root folder of the UPPAAL distribution (i.e. ```$UPPAAL_ROOT/uppaal.jar``` should point to the main jar file). Make sure you use Uppaal version 4.1.23 or higher.
##### Ensure plugin folder exists
Plugins should be placed in the plugins directory inside Uppaal. Make sure that ```$UPPAAL_ROOT/plugins/``` exists, make it if it doesn't exist.

### Available commands
> Windows should ```gradlew.bat``` instead of ```./gradlew```  
> **Use Java 8 (due to Xtend compatability issues with new Java versions)**
##### Build
Run ```./gradlew build``` to build the plugin. The plugin can be found ```build/libs/```
##### Build and deploy local
Run ```./gradlew deployLocal``` to build and copy the plugin into the plugin directory of Uppaal.
##### Build, deploy and run
To build the plugin, copy it to the Uppaal plugins directory, and run Uppaal afterwards, use ```./gradlew runUppaal```

##### IDE setup
The project is set-up using Gradle, meaning that any Java IDE with a Gradle plugin should work.  
The base language is Java, however, support for Kotlin is present.

### Usage

The plugin adds a tab to the Uppaal editor where the user can specify a list of checks to which the model must conform. These checks can either be model checked using Uppaal SMC (meaning simulations of the model are used to find unsafe states), or using the Uppaal model checker (which uses an abstraction to convert the Uppaal SMC model to a pure Uppaal timed automata).

#### Current Checks

##### Model invariant

Specify a condition which must be true in all states of the model. This is a very general check, and can be used to form a broad specification to which the model must conform during the development process. 

##### Receive synchronisations

A common pattern in Uppaal model is to use channels to synchronise two processes (for example, after firing a synchronisation c!, the receiving process must always transition to some location). This check specifies that given a channel c, a process must in all reachable states have an enabled receiving synchronisation edge c? enabled when another process fires a c! edge (unless the ignore condition is true).

##### More checks coming soon

Next up is a check to specify a post-condition which must be true after some synchronisation transition is taken. Suggestions for other useful checks are welcome.

##### Urpal checks

A number of checks of the Urpal plugin (currently 2, more to follow) are supported. Full explanation of these checks can be found in the accompanying [thesis](https://fmt.ewi.utwente.nl/media/thesis_main.pdf). Only symbolic model checking is supported for these checks. 

#### Checker options

##### Concrete

This option uses the run generation of Uppaal SMC to simulate the transformed system, and filter on the specified property. This option does not rely on model checking algorithms (avoiding the problems with state space explosion), but might be unsuitable to find rare events (as is a limitation of monte carlo simulation).

##### Symbolic

This option works by abstracting the model to a 'pure' Uppaal timed automata model by hiding clocks with a variable clock rate. Then, the model checker is used to search the state space for the specified property. Since an abstraction of the system is checked, false positives might occur. Most suitable for properties related to the discrete state of the system, and not properties dependant on the continuous dynamics of the model. 

#### Traces

If an unsafe state is found to be reachable, a trace leading to this state can be loaded in the editor. This trace can be used to debug the model, and see exactly how it was possible to reach the state. 

#### Overwrite constants

Any variables marked const in the model can be overwritten before model checking. This can be used to reduce the number of process instances, reducing the state space for symbolic model checking.  

#### Time constraints

Limit the model to be unable to take time transitions after a specified amount of time. Useful if the model has no upper limit on time, and symbolic checking is used.

#### Known issues

When loading a trace in the editor, Uppaal will show a prompt asking to asking to upload the new model. If this happens, press no (otherwise the original, unchanged model is loaded and the trace cannot be shown).

### Documentation
See the [wiki](https://github.com/utwente-fmt/UrPal/wiki)
