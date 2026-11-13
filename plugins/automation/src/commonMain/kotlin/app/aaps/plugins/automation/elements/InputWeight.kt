package app.aaps.plugins.automation.elements

/** autoISF: a value of an ISF weight, for example `bgAccel_ISF_weight`. */
class InputWeight() {

    var value = 0.0

    constructor(another: Double) : this() {
        value = another
    }

    companion object {

        const val MIN = 0.0
        const val MAX = 1.0
    }
}
