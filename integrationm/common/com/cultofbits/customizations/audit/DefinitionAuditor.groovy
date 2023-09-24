package com.cultofbits.customizations.calc

import com.cultofbits.integrationm.service.dictionary.recordm.Definition
import com.cultofbits.integrationm.service.dictionary.recordm.FieldDefinition
import com.cultofbits.integrationm.service.dictionary.recordm.RecordmMsg

import java.math.RoundingMode

class DefinitionAuditor {

    private DEBUG = false

    protected String defName;
    protected Integer defVersion;

    // All field definitions that have $calc.<operation>
    protected Map<Integer, AuditExpr> auditExprMapById = [:]

    // log4j
    private Object log

    DefinitionCalculator(definition) {
        this.defName = definition.name
        this.defVersion = definition.version

        this.processDefinition(definition)
    }

    DefinitionCalculator(definition, log) {
        this.log = log
        this.defName = definition.name
        this.defVersion = definition.version

        this.processDefinition(definition)
    }

    protected processDefinition(Definition definition) {
        definition.findFields { true }
                .each { m ->
                    def matcher = fd.description =~ /[$]audit\.(creator|updater)\.(username|uri|time)/

                    if (matcher.matches()) {
                        auditExprMapById << [(fd.id): new AuditExpr().with {
                            it.operation = matcher[0][1]
                            it.arg = matcher[0][2]
                        }]
                    }
                }
    }

    private logMessage(message) {
        if (!DEBUG) return

        if (log != null) {
            log.info(message)

        } else {
            println(message)
        }
    }

    /**
     * Perform calculation in the necessary instance fields
     * @param recordmMsg the recordm event message
     * @return a Map with all the updated fields
     */
    Map<String, String> audit(RecordmMsg recordmMsg) {
        if (auditExprMapById.isEmpty() &&
                recordmMsg.action != "add"
                && !recordmMsg.getInstance().getFields().any { f -> recordmMsg.field(f.fieldDefinition.name).changed() }) {
            return [:]
        }

        logMessage("[_calc] instanceId=${calcContext.recordmMsg.instance.id} \n" +
                "auditExprMapById=\n    " + auditExprMapById.collect { k, v -> "${k} -> ${v}" }.join("\n    ") + "\n")

        auditExprMapById.eachWithIndex { Entry<Integer, AuditExpr> entry, int i ->

        }

        recordmMsg.getInstance().getFields().inject([:] as Map<String, String>) { map, Map<String, Object> field ->
            def newValue = getFieldValue(recordmMsg, field)

            if (newValue != field.value) {
                map << [("id:${field.id}".toString()): newValue]
            }

            map
        } as Map<String, String>
    }

    /**
     * Calculate a single field value
     */
    String getFieldValue(recordmMsg, auditExpr) {

        if (auditExpr.op == "creator" && recordmMsg.action == "update") return
        if (auditExpr.op == "update" && recordmMsg.getI)

        switch (auditExpr.operation) {
            case "create":
                result = 1
                flattenArgValues.each { result = result.multiply(it) }
                break;
            case "divide":
                if (flattenArgValues.size() == 2 && (flattenArgValues[1] ?: 0 != 0)) {
                    result = flattenArgValues[0]
                    result = result.divide(flattenArgValues[1], 8, RoundingMode.HALF_UP)
                }
                break;
            case "sum":
                flattenArgValues.each { result = result + it }
                break;
            case "subtract":
                if (flattenArgValues.size() == 2) {
                    result = flattenArgValues[0]
                    result = result.subtract(flattenArgValues[1])
                }
                break;
            case "diffDays":
                if (flattenArgValues.size() == 2) {
                    result = flattenArgValues[0]
                    result = result.subtract(flattenArgValues[1])
                    result = result.divide(new BigDecimal(24 * 60 * 60 * 1000), 8, RoundingMode.HALF_UP)
                }
                break;
            case "diffHours":
                if (flattenArgValues.size() == 2) {
                    result = flattenArgValues[0]
                    result = result.subtract(flattenArgValues[1])
                    result = result.divide(new BigDecimal(60 * 60 * 1000), 8, RoundingMode.HALF_UP)

                }
                break;
            case "diifMinutes":
                if (flattenArgValues.size() == 2) {
                    result = flattenArgValues[0]
                    result = result.subtract(flattenArgValues[1])
                    result = result.divide(new BigDecimal(60 * 1000), 8, RoundingMode.HALF_UP)
                }
                break;
            default:
                throw new IllegalArgumentException("[_calc] Unknown operation instance instanceId=${calcContext.recordmMsg.instance.id} " +
                        "operation=${auditExpr.operation}")
        }

        result = result.stripTrailingZeros().toPlainString()
        calcContext.cache[field.fieldDefinition.id] = result

        logMessage("[_calc] instanceId=${calcContext.recordmMsg.instance.id} \n" +
                "calcExpr=${auditExpr}" +
                "\n   fieldId=${field.id} fieldDefinitionName=${field.fieldDefinition.name} " +
                "\n   args=${auditExpr.args} " +
                "\n   fields=${auditExpr.args.collect { arg -> fdVarsMapByVarName[arg].collect { fd -> calcContext.fieldMapByFieldDefId[fd.id].collect { it.id }.join(',') } }} " +
                "\n   argValues=${argValues} " +
                "\n   flattenArgValues=${flattenArgValues} " +
                "\n   result=${result}")

        result
    }

    /**
     * Auxiliary class that represents the operation to perform
     */
    static class AuditExpr {
        FieldDefinition fieldDefinition;

        // The audit operation. One of [creator,updater]
        String operation;

        // The audit argument. One of [username,uri,time]
        String arg;

        @Override
        String toString() {
            return "audit.${operation}${arg}"
        }
    }

    /**
     * Auxiliary class that holds instance information and provides methods to facilitate the calculation
     */
    static class CalcContext {
        RecordmMsg recordmMsg
        Map<Integer, List<Map<String, Object>>> fieldMapByFieldDefId
        Map<Integer, BigDecimal> cache

        CalcContext(recordmMsg) {
            this.recordmMsg = recordmMsg
            this.cache = [:]
            this.fieldMapByFieldDefId = recordmMsg.instance.getFields().inject([:] as Map<Integer, List<Map<String, Object>>>) { map, field ->
                def fieldDefIdEntry = map[field.fieldDefinition.id]
                if (fieldDefIdEntry != null) {
                    fieldDefIdEntry << field
                } else {
                    map << [(field.fieldDefinition.id): [field]]
                }
                map
            }
        }
    }
}