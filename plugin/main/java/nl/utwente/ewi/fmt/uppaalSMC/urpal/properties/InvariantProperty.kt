package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.ValidationSpec

@SanityCheck(name = "Model Invariant", shortName = "invariant")
class InvariantProperty : SafetyProperty() {
    override fun translateNSTA(nsta: NSTA, config: ValidationSpec.PropertyConfiguration): String {
        val cond = config.parameters["condition"] ?: error("Missing parameter 'condition'")

        return "!($cond)"
    }

    override fun getParameters(): List<PropertyParameter> {
        return super.getParameters() + listOf(PropertyParameter("condition", "Condition", ArgumentType.STRING))
    }
}