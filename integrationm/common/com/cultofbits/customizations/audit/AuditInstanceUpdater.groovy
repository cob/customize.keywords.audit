package com.cultofbits.customizations.audit

import com.cultofbits.integrationm.service.actionpack.RecordmActionPack
import com.cultofbits.integrationm.service.actionpack.UsermActionPack
import com.cultofbits.integrationm.service.dictionary.recordm.Definition
import com.cultofbits.integrationm.service.dictionary.recordm.FieldDefinition
import com.cultofbits.integrationm.service.dictionary.recordm.RecordmMsg
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.json.JSONObject

class AuditInstanceUpdater {

    private static Cache<String, Definition> globalCache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .build();

    private final RecordmActionPack recordmActionPack
    private final UsermActionPack usermActionPack
    private final Object log

    AuditInstanceUpdater(RecordmActionPack recordmActionPack, UsermActionPack usermActionPack, log) {
        this.recordmActionPack = recordmActionPack
        this.usermActionPack = usermActionPack
        this.log = log
    }

    Definition getDefinitionFromCache(RecordmMsg msg) {
        Definition definition = globalCache.get(msg.type, { recordmActionPack.getDefinition(msg.getDefinitionName()).getBody() })

        if (definition.getVersion() != msg.getDefinitionVersion()) {
            globalCache.invalidate(msg.type)
            definition = globalCache.get(msg.type, { recordmActionPack.getDefinition(msg.getDefinitionName()).getBody() })
        }

        return definition
    }

    def getAuditFields(RecordmMsg msg) {
        Definition definition = getDefinitionFromCache(msg)

        def nonRefFieldChanged = false

        def regex = /[$]audit\.(creator|updater)\.(username|uri|time)/
        def auditFields = [];
        for (FieldDefinition fd in definition.fieldDefinitions) {
            if (msg.field(fd.name).changed() && !fd.configuration.keys.containsKey("AutoRefField")) {
                nonRefFieldChanged = true
                break
            }
        }
        for (FieldDefinition fd in definition.fieldDefinitions) {
            def matcher = fd =~ regex
            if (matcher) {
                if (msg.action == 'update' && !nonRefFieldChanged && ignoreRefsChanges(fd.configuration.extensions)) {
                    continue
                }

                def op = matcher[0][1]
                def arg = matcher[0][2]
                auditFields << [fieldId: fd.id, name: fd.name, op: op, args: arg]
            }
        }

        log?.info("[\$audit] Update 'auditFields' for '${msg.getDefinitionName()}': $auditFields");

        return auditFields
    }

    /***
     *
     * @return a map of the fields that changed. The map is <fieldName,(username|uri|time)>
     * @username: username of the user that changed the value of the field
     * @uri: uri of the user that changed the value of the field
     * @time: time of when the field value was update or changed
     */
    LinkedHashMap<String, String> getAuditFieldsUpdates(List<Map> auditFields, RecordmMsg msg) {
        LinkedHashMap<String, String> updates = [:]

        auditFields.each { auditField ->
            if (auditField.op == "creator" && msg.action == "update" && msg.value(auditField.name) != null) {
                // 'creator' fields are only changed in 'update' if the previous value was empty (meaning it was a field that was not visible)
                return
            }

            if (auditField.args == "uri") {
                updates[auditField.name] = usermActionPack.getUser(msg.user).getBody()._links.self

            } else if (auditField.args == "username") {
                updates[auditField.name] = msg.user

            } else if (auditField.args == "time") {
                if (msg.action == 'add' && msg.value(auditField.name, Long.class) ?: msg.getTimestamp() < 30000) return
                // Ignore changes less then 30s
                updates[auditField.name] = msg.getTimestamp().toString()
            }
        }

        return updates
    }

    /**
     * @return true if the JSONObject extraKeywords has an audit description with an argument of ignoreRefs:true
     * Example: $audit.updater.time(ignoreRefs:true)
     */
    def private static ignoreRefsChanges(Map extraKeywords) {
        def rgx = /[$]audit\.(creator|updater)\.(username|uri|time)/

        for (def key in extraKeywords.keySet()) {
            if (key =~ rgx) {
                JSONObject jsonObj = new JSONObject(extraKeywords.get(key))
                if (jsonObj.has("args")) {
                    def args = jsonObj.getJSONArray("args")
                    if (args.length() > 0) {
                        return toMapArgs(args.optString(0))["ignoreRefs"] == "true"
                    }
                }
                return false
            }
        }
        return false
    }

    /**
     * @stringArgs a string with the options of audit. Eg.:"[ignoreRefs:true,a:1]"
     * @return a map containing the elements in the string
     * */
    def private static toMapArgs(stringArgs) {
        if (stringArgs == null) {
            return [:]
        }

        return stringArgs
                .replaceAll("^\\[", "")
                .replaceAll("]\$", "")
                .replaceAll("\\\\,", "@COB_ENC_COMMA@")
                .replaceAll("\\\\:", "@COB_ENC_COLON@")
                .split(",")
                .collectEntries {
                    it = it.split(":")
                    if (it.length > 1) {
                        [it[0].trim(),
                         it[1].trim()
                                 .replaceAll("@COB_ENC_COMMA@", ",")
                                 .replaceAll("@COB_ENC_COLON@", ":")]
                    } else {
                        [:]
                    }
                };
    }
}