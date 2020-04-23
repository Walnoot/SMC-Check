package nl.utwente.ewi.fmt.uppaalSMC.urpal.util

import com.uppaal.engine.EngineException
import com.uppaal.model.core2.Document
import com.uppaal.model.system.UppaalSystem
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.AbstractProperty
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.SanityCheckResult
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.MainUI
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.io.IOException

/**
 * A model of the validation specification used.
 */
class ValidationSpec(spec: String) {
    private val listeners = mutableListOf<ValidationListener>()

    private val changeListeners = mutableListOf<() -> Unit>()

    private val constants: MutableMap<String, String>

    private val configs: MutableList<PropertyConfiguration>

    /**
     * If non-empty, the model is changed such that no time transitions are possible after the specified amount of time-
     * units have passed.
     */
    var timeLimit: String = ""
        set(value) {
            field = value
            stateChanged()
        }

    /**
     * If not null, the clock whose value cannot exceed timeLimit.
     */
    var timeLimitClock: String? = null
        set(value) {
            field = value
            stateChanged()
        }

    val propertyConfigurations: List<PropertyConfiguration> get() = configs

    /**
     * A map of variable names to an override string value. Keys are names of constant variables in the global
     * declaration of the model, values are UPPAAL literals (either integers or booleans, no type-checking is performed
     * by the validation tool).
     */
    val overrideConstants: Map<String, String> get() = constants

    /**
     * Whether the model should disallow time transitions after some limit is reached.
     */
    val timeLimitEnabled get() = timeLimit.isNotBlank()

    init {
        val json: JSONObject = JSONParser().parse(spec) as JSONObject

        constants = mutableMapOf<String, String>()

        if ("constants" in json) {
            for ((key, value) in (json["constants"] as JSONObject)) {
                constants[key as String] = value as String
            }
        }

        configs = mutableListOf<PropertyConfiguration>()

        val jsonChecks = json.getOrDefault("checks", JSONArray()) as JSONArray
        for (jsonConfig in jsonChecks.map { it as JSONObject }) {
            val type = jsonConfig["type"] as String
            val prop = AbstractProperty.properties.find { it.shortName() == type }

            if (prop != null) {
                val params = mutableMapOf<String, String>()
                if ("params" in jsonConfig) {
                    val jsonParams = jsonConfig["params"] as JSONObject

                    for (entry in jsonParams) {
                        params[entry.key as String] = entry.value as String
                    }
                }

                configs.add(PropertyConfiguration(prop, params))
            } else {
                println("Unknown property type '$type'")
            }
        }

        if ("time" in json) {
            timeLimit = json["time"] as String
        } else {
            timeLimit = ""
        }

        if ("time_limit_clock" in json) {
            timeLimitClock = json["time_limit_clock"] as String
        }
    }

    fun toJSON (): JSONObject {
        val result = JSONObject()

        val constantsJson = JSONObject()
        result["constants"] = constantsJson

        for ((key, value) in constants.entries) {
            constantsJson[key] = value
        }

        val jsonConfigs = JSONArray()
        result["checks"] = jsonConfigs

        for (config in configs) {
            val configJson = JSONObject()
            jsonConfigs.add(configJson)

            configJson["type"] = config.property.shortName()

            val paramsJson = JSONObject()
            configJson["params"] = paramsJson

            for ((key, value) in config.parameters.entries) {
                paramsJson[key] = value
            }
        }

        result["time"] = timeLimit

        if (timeLimitClock != null) {
            result["time_limit_clock"] = timeLimitClock
        }

        return result
    }

    fun check(doc: Document): List<SanityCheckResult> {
        val results = mutableListOf<SanityCheckResult>()

        val nsta = MainUI.load(doc)
        val sys: UppaalSystem
        try {
            sys = UppaalUtil.compile(doc)
        } catch (e: EngineException) {
            e.printStackTrace()
//            output?.writeText("compile error\n")
            return emptyList()
        } catch (e: IOException) {
            e.printStackTrace()
            return emptyList()
        }

        for (check in configs) {
            val prop = check.property
            val result = prop.check(nsta, doc, sys, check)
            results.add(result)

            println("done check $check (${result.message})")

            for (l in listeners) {
                l.onCheckFinished(check, result)
            }
        }

        return results
    }

    fun addValidationListener(listener: ValidationListener) {
        listeners.add(listener)
    }

    /**
     * Add a listener that is called on every internal state change of this object.
     */
    fun addChangeListener(listener: () -> Unit) {
        changeListeners.add(listener)
    }

    fun setOverrideConstant(constant: String, value: String) {
        constants[constant] = value
        stateChanged()
    }

    fun clearOverrideConstants() {
        constants.clear()
        stateChanged()
    }

    fun addPropertyConfiguartion (prop: AbstractProperty) : PropertyConfiguration {
        val config = PropertyConfiguration(prop, mutableMapOf())

        configs.add(config)

        stateChanged()

        return config
    }

    fun removePropertyConfiguration(configuration: PropertyConfiguration) {
        configs.remove(configuration)
        stateChanged()
    }

    private fun stateChanged () {
        for (l in changeListeners) {
            l.invoke()
        }
    }

    inner class PropertyConfiguration (val property: AbstractProperty, private val mutParameters: MutableMap<String, String>) {
        val spec = this@ValidationSpec

        // expose immutable view
        val parameters: Map<String, String> = mutParameters

        fun setParameter(key: String, value: String) {
            mutParameters[key] = value
            spec.stateChanged()
        }
    }
}