package com.cultofbits.customizations.calc

import com.cultofbits.customizations.utils.RecordmMsgBuilder
import spock.lang.Specification

import static com.cultofbits.customizations.utils.DefinitionBuilder.aDefinition
import static com.cultofbits.customizations.utils.FieldDefinitionBuilder.aFieldDefinition


class DefinitionCalculatorCalculationStepTest extends Specification {

    void "can calculate with simple definition"() {
        given:
        def definition = aDefinition()
                .fieldDefinitions(
                        aFieldDefinition().id(1).description("\$var.field1 dummy text"),
                        aFieldDefinition().id(2).description("\$number(2) \$var.field2 \$hint[some hint]"),
                        aFieldDefinition().id(3).description("\$var.some.var.structure \$hint[some hint]"),
                        aFieldDefinition().id(4).description("\$calc.sum(var.field1,var.field2,10,var.nullValue)"),
                        aFieldDefinition().id(5).name("nullValue").description("\$var.nullValue"),
                ).build()


        def recordmMsg = RecordmMsgBuilder.aMessage("admin", definition, "add")
                .newField(definition, 1, 101, "1000")
                .newField(definition, 2, 102, "1")
                .newField(definition, 3, 103, "2")
                .newField(definition, 4, 104, null)
                .newField(definition, 5, 105, null)
                .build()

        when:
        def calculator = new DefinitionCalculator(definition)
        def updateMap = calculator.calculate(recordmMsg)

        then:
        updateMap["id:104"] == "${1000 + 1 + 10}".toString()
    }

    void "can calculate chained calculations"() {
        given:
        def definition = aDefinition().fieldDefinitions(
                aFieldDefinition().id(1).description("field0"),
                aFieldDefinition().id(2).description("\$var.field1"),
                aFieldDefinition().id(3).description("\$calc.multiply(var.field1,2) \$var.field2"),
                aFieldDefinition().id(4).description("\$calc.sum(var.field1,var.field2,10,var.nullValue)"),
                aFieldDefinition().id(5).name("field4-nullValue").description("\$var.nullValue"),
        ).build()

        def recordmMsg = RecordmMsgBuilder.aMessage("admin", definition, "add")
                .newField(definition, 1, 101, "1000")
                .newField(definition, 2, 102, "50")
                .newField(definition, 3, 103, null)
                .newField(definition, 4, 104, null)
                .newField(definition, 5, 105, null)
                .build()

        when:
        def calculator = new DefinitionCalculator(definition)
        def updateMap = calculator.calculate(recordmMsg)

        then:
        updateMap["id:103"] == "${50 * 2}".toString()
        updateMap["id:104"] == "${50 + (50 * 2) + 10 + 0}".toString()
    }

    void "can calculate chained calculations with fields located after"() {
        given:
        def definition = aDefinition().fieldDefinitions(
                aFieldDefinition().id(1).description("field1"),
                aFieldDefinition().id(2).description("\$var.field2"),
                aFieldDefinition().id(3).description("\$calc.sum(var.field2,var.field4,10,var.nullValue)"),
                aFieldDefinition().id(4).description("\$calc.multiply(var.field2,2) \$var.field4"),
                aFieldDefinition().id(5).name("field5-nullValue").description("\$var.nullValue"),
        ).build()

        def recordmMsg = RecordmMsgBuilder.aMessage("admin", definition, "add")
                .newField(definition, 1, 101, "1000")
                .newField(definition, 2, 102, "50")
                .newField(definition, 3, 103, null)
                .newField(definition, 4, 104, null)
                .newField(definition, 5, 105, null)
                .build()

        when:
        def calculator = new DefinitionCalculator(definition)
        def updateMap = calculator.calculate(recordmMsg)

        then:
        updateMap["id:103"] == "${50 + (50 * 2) + 10}".toString()
        updateMap["id:104"] == "${50 * 2}".toString()
    }

    void "can handle circular dependencies by throwing an error"() {
        given:
        def definition = aDefinition().fieldDefinitions(
                aFieldDefinition().id(1).description("field1"),
                aFieldDefinition().id(2).description("\$calc.multiply(var.field3,2) \$var.field2"),
                aFieldDefinition().id(3).description("\$calc.multiply(var.field2,2) \$var.field3"),
        ).build()

        def recordmMsg = RecordmMsgBuilder.aMessage("admin", definition, "add")
                .newField(definition, 1, 101, "1000")
                .newField(definition, 2, 102, "50")
                .newField(definition, 3, 103, null)
                .build()

        when:
        def calculator = new DefinitionCalculator(definition)
        calculator.calculate(recordmMsg)

        then:
        def e = thrown(RuntimeException)
        e.getMessage() == "Cyclic dependency detected, path: fd:2,field-definition-2 -> fd:3,field-definition-3 -> fd:2,field-definition-2"
    }

    void "can calculate with 'previous' calc arg"() {
        given:
        def definition = aDefinition().fieldDefinitions(
                aFieldDefinition().id(1),
                aFieldDefinition().id(2).description("\$calc.multiply(previous,2)"),
                aFieldDefinition().id(3).description("\$calc.multiply(previous,2)"),
        ).build()

        def recordmMsg = RecordmMsgBuilder.aMessage("admin", definition, "add")
                .newField(definition, 1, 101, "1000")
                .newField(definition, 2, 102, null)
                .newField(definition, 3, 103, null)
                .build()

        when:
        def calculator = new DefinitionCalculator(definition)
        def updateMap = calculator.calculate(recordmMsg)

        then:
        updateMap["id:102"] == "${1000 * 2}".toString()
        updateMap["id:103"] == "${(1000 * 2) * 2}".toString()
    }

    void "can calculate with subparts"() {
        given:
        def definition = aDefinition().fieldDefinitions(
                aFieldDefinition().id(1).description("\$var.total.chickens"),
                aFieldDefinition().id(2).description("\$var.total.dogs.small"),
                aFieldDefinition().id(3).description("\$var.total.dogs.big"),

                aFieldDefinition().id(4).description("\$calc.sum(var.total.dogs)"),
                aFieldDefinition().id(5).description("\$calc.sum(var.total)"),
        ).build()

        def recordmMsg = RecordmMsgBuilder.aMessage("admin", definition, "add")
                .newField(definition, 1, 101, "10")
                .newField(definition, 2, 102, "5")
                .newField(definition, 3, 103, "2")
        // calcs
                .newField(definition, 4, 104, null)
                .newField(definition, 5, 105, null)
                .build()

        when:
        def calculator = new DefinitionCalculator(definition)
        def updateMap = calculator.calculate(recordmMsg)

        then:
        updateMap["id:104"] == "${5 + 2}".toString()
        updateMap["id:105"] == "${10 + 5 + 2}".toString()
    }

    void "throw error when calculating instance with definition in invalid state"() {
        given:
        def definition = aDefinition().fieldDefinitions(
                aFieldDefinition().id(2).description("\$calc.multiply(previous,2)"),
                aFieldDefinition().id(3).description("\$calc.multiply(previous,2)"),
        ).build()

        def recordmMsg = RecordmMsgBuilder.aMessage("admin", definition, "add")
                .newField(definition, 2, 102, null)
                .newField(definition, 3, 103, null)
                .build()

        when:
        def calculator = new DefinitionCalculator(definition)
        calculator.calculate(recordmMsg)

        then:
        def e = thrown(IllegalStateException)
        e.getMessage() == "[_calc] instanceId=null definition is in invalid state to calculate {{errorMessage:No previous field available for field " +
                "FieldDefinition{id=2, name='field-definition-2', description='\$calc.multiply(previous,2)', duplicable=false, required=null} }}"
    }

    void "ignore invisible fields"() {
        given:
        def definition = aDefinition().fieldDefinitions(
                aFieldDefinition().id(1).description("field1"),
                aFieldDefinition().id(2).description("\$var.field2"),
                aFieldDefinition().id(3).description("\$var.fieldwithsamename"),
                aFieldDefinition().id(4).description("\$var.fieldwithsamename \$hint[should not be part of the message]"),
                aFieldDefinition().id(5).description("\$calc.multiply(var.field2,var.fieldwithsamename)"),
        ).build()

        def recordmMsg = RecordmMsgBuilder.aMessage("admin", definition, "add")
                .newField(definition, 1, 101, "1000")
                .newField(definition, 2, 102, "1")
                .newField(definition, 3, 103, "10")
                .newField(definition, 5, 105, null)
                .build()

        when:
        def calculator = new DefinitionCalculator(definition)
        def updateMap = calculator.calculate(recordmMsg)

        then:
        updateMap["id:105"] == "${1 * 10}".toString()
    }

    void "ignore trailing zeros"() {
        given:
        def definition = aDefinition().fieldDefinitions(
                aFieldDefinition().id(1).description("\$number(2) \$var.field1"),
                aFieldDefinition().id(2).description("\$calc.multiply(var.field1,280,10)"),
        ).build()

        def recordmMsg = RecordmMsgBuilder.aMessage("admin", definition, "add")
                .newField(definition, 1, 101, "0.4")
                .newField(definition, 2, 102, null)
                .build()

        when:
        def calculator = new DefinitionCalculator(definition)
        def updateMap = calculator.calculate(recordmMsg)

        then:
        updateMap["id:102"] == "1120"
    }
}
