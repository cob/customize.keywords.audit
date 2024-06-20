package com.cultofbits.customizations.audit

import com.cultofbits.customizations.utils.FieldDefinitionBuilder
import com.cultofbits.integrationm.service.actionpack.RecordmActionPack
import com.cultofbits.integrationm.service.actionpack.UsermActionPack
import com.cultofbits.integrationm.service.dictionary.ReusableResponse
import com.cultofbits.customizations.utils.RecordmMsgBuilder
import com.cultofbits.integrationm.service.dictionary.recordm.Definition
import com.cultofbits.integrationm.service.dictionary.recordm.FieldDefinition
import com.cultofbits.integrationm.service.dictionary.recordm.RecordmMsg
import com.cultofbits.integrationm.service.dictionary.userm.User
import spock.lang.Specification

import static com.cultofbits.customizations.utils.DefinitionBuilder.aDefinition
import static com.cultofbits.customizations.utils.FieldDefinitionBuilder.aFieldDefinition

class AuditUtilsTest extends Specification {

    def getDefinition() {
        return aDefinition()
                .id(1)
                .name("Audit Definition")
                .fieldDefinitions(
                        aFieldDefinition().id(11).name("Text").description("\$text"),
                        aFieldDefinition().id(12).name("Creator").description("\$audit.creator.username"),
                        aFieldDefinition().id(13).name("TimeCreation").description("\$audit.updater.time"),
                        aFieldDefinition().id(14).name("RefField").description("")
                                .setConfigurationKey("AutoRefField", "{\"args\":{\"source_field\":\"client\",\"field_name\":\"age\"}}")
                )
    }

    def getDefinition(String name) {
        def definition = getDefinition()
        definition.name = name
        return definition
    }

    def FieldDefinition buildFieldDefinition(int fieldId, String fieldName, String fieldDescription, String fieldConfigurationKey, String fieldConfiguration) {
        FieldDefinitionBuilder builder = aFieldDefinition()
                .id(fieldId)
                .name(fieldName)
                .description(fieldDescription)

        if (fieldConfiguration != null) {
            builder.setConfigurationExtensions(fieldConfigurationKey, fieldConfiguration);
        }

        return builder.build()
    }

    def "can detect \$audit fields on add"() {

        def definition = getDefinition().build()
        def newInstanceMsg = RecordmMsgBuilder.aMessage("cob-admin", definition, "add")
                .build()

        AuditInstanceUpdater auditInstanceUpdated = new AuditInstanceUpdater(getRecordmActionPack(definition), null, null)

        def auditFields = auditInstanceUpdated.getAuditFields(newInstanceMsg)

        expect:
        auditFields.size() == 2
        auditFields[0].name == "Creator"
        auditFields[1].name == "TimeCreation"
    }

    def "can detect \$audit fields on update with no audit arguments"() {

        def definition = getDefinition().build()

        //audit without arguments
        def newInstanceMsg = RecordmMsgBuilder.aMessage("cob-admin", definition, "update").newField(definition.getFirstField("Text"), "text")
                .build()

        AuditInstanceUpdater auditInstanceUpdated = new AuditInstanceUpdater(getRecordmActionPack(definition), null, null)

        def auditFields = auditInstanceUpdated.getAuditFields(newInstanceMsg)

        expect:
        auditFields.size() == 2
        auditFields[0].name == "Creator"
        auditFields[1].name == "TimeCreation"

    }

    def "can detect \$audit fields on update with audit arguments [ignoreRefs:false,a:1] or [ignoreRefs:true,a:1]"() {

        def definitionBuilder = getDefinition()

        def definition = definitionBuilder.addFieldDefinitions(
                buildFieldDefinition(14, "TimeCreation2", "\$audit.updater.uri([ignoreRefs:false,a:1])", "\$audit.updater.uri", "{\"args\":[\"[ignoreRefs:false,a:1]\"]}"),
                buildFieldDefinition(16, "TimeCreation3", "\$audit.updater.username([ignoreRefs:true,a:1])", "\$audit.updater.username", "{\"args\":[\"[ignoreRefs:true,a:1]\"]}")
        ).build();


        //audit without arguments
        def newInstanceMsg = RecordmMsgBuilder.aMessage("cob-admin", definition, "update").newField(definition.getFirstField("Text"), "text")
                .build()
        RecordmMsg.Field f = Mock(RecordmMsg.Field.class)
        f.changed() >> false
        newInstanceMsg.field("") >> f

        AuditInstanceUpdater auditInstanceUpdated = new AuditInstanceUpdater(getRecordmActionPack(definition), null, null)


        def auditFields = auditInstanceUpdated.getAuditFields(newInstanceMsg)

        expect:
        definition.rootFields.size() == 6
        auditFields.size() == 4
        auditFields[0].name == "Creator"
        auditFields[0].op == "creator"
        auditFields[0].args == "username"


        auditFields[1].name == "TimeCreation"
        auditFields[1].op == "updater"
        auditFields[1].args == "time"


        auditFields[2].name == "TimeCreation2"
        auditFields[2].op == "updater"
        auditFields[2].args == "uri"


        auditFields[3].name == "TimeCreation3"
        auditFields[3].op == "updater"
        auditFields[3].args == "username"
    }

    def "when none of the nonRef fields change and ignore audit is true"() {
        def definitionBuilder = getDefinition()

        def definition = definitionBuilder.addFieldDefinitions(
                buildFieldDefinition(14, "TimeCreation2", "\$audit.updater.uri([ignoreRefs:false,a:1])", "\$audit.updater.time", "{\"args\":[\"[ignoreRefs:false,a:1]\"]}"),
                buildFieldDefinition(16, "TimeCreation3", "\$audit.updater.username([ignoreRefs:true,a:1])", "\$audit.updater.time", "{\"args\":[\"[ignoreRefs:true,a:1]\"]}")
        ).build();

        RecordmMsg newInstanceMsg = RecordmMsgBuilder.aMessage("cob-admin", definition, "update").build()
        RecordmMsg.Field f = Stub()
        f.changed() >> false
        newInstanceMsg.field("") >> f

        AuditInstanceUpdater auditInstanceUpdated = new AuditInstanceUpdater(getRecordmActionPack(definition), null, null)

        def auditFields = auditInstanceUpdated.getAuditFields(newInstanceMsg)

        expect:
        auditFields.size() == 3
    }

    def "when at least one nonRef field changes and ignore audit is true"() {
        def definitionBuilder = getDefinition()

        def definition = definitionBuilder.addFieldDefinitions(
                buildFieldDefinition(15, "TimeCreation2", "\$audit.updater.uri([ignoreRefs:false,a:1])", null, null),
                buildFieldDefinition(16, "TimeCreation3", "\$audit.updater.username([ignoreRefs:true,a:1])", null, null)
        ).build();

        //audit without arguments
        def newInstanceMsg = RecordmMsgBuilder.aMessage("cob-admin", definition, "update").newField(definition.getFirstField("Text"), "text")
                .updatedField(definition.getField(11), "oldValue", "")
                .build()

        AuditInstanceUpdater auditInstanceUpdated = new AuditInstanceUpdater(getRecordmActionPack(definition), null, null)

        def auditFields = auditInstanceUpdated.getAuditFields(newInstanceMsg)

        expect:
        auditFields.size() == 4
    }

    def "getAuditFieldsUpdates"() {
        def definitionBuilder = getDefinition()
        def definition = definitionBuilder.build()

        def auditFields = [
                [fieldId: 1, name: "field7", op: "updater", args: "time"],
                [fieldId: 3, name: "field9", op: "updater", args: "username"]
        ];

        def newInstanceMsg = mockInstanceMSG()


        AuditInstanceUpdater auditInstanceUpdated = new AuditInstanceUpdater(getRecordmActionPack(definitionBuilder.build()), null, null)

        def fieldsToUpdate = auditInstanceUpdated.getAuditFieldsUpdates(auditFields, newInstanceMsg)

        expect:
        fieldsToUpdate.size() == 2
    }

    def "getAuditFieldsUpdates uri link"() {
        def definitionBuilder = getDefinition()
        def definition = definitionBuilder.build()

        def auditFields = [[fieldId: 1, name: "field7", op: "updater", args: "uri"]];

        def newInstanceMsg =  mockInstanceMSG()

        User user = new User()
        user.username = newInstanceMsg.user
        user.name = newInstanceMsg.user
        def links = new User.UserLinks()
        links.self = "userm.com"
        user._links = links

        ReusableResponse<User> reusableResponse = Mock()
        reusableResponse.getBody() >> user
        UsermActionPack usermActionPack = Mock(UsermActionPack.class)
        usermActionPack.getUser(newInstanceMsg.user) >> reusableResponse

        AuditInstanceUpdater auditInstanceUpdated = new AuditInstanceUpdater(getRecordmActionPack(definitionBuilder.build()), usermActionPack, null)

        def fieldsToUpdate = auditInstanceUpdated.getAuditFieldsUpdates(auditFields, newInstanceMsg)

        expect:
        fieldsToUpdate.size() == 1
        fieldsToUpdate[auditFields[0].name] == "userm.com"
    }

    def "test cache"() {
        def cache = AuditInstanceUpdater.getCache()

        def definition = getDefinition().build()

        def cachedDefinition1 = getCachedDefinitionAux(definition)

        expect:
        cache != null
        cache.size() == 1
        cachedDefinition1.hashCode() == definition.hashCode()
    }
    RecordmActionPack getRecordmActionPack(definition){
        def reusableResponse = Stub(ReusableResponse.class)
        reusableResponse.getBody() >> definition

        def rmActionPack = Stub(RecordmActionPack.class)
        rmActionPack.getDefinition(definition.name) >> reusableResponse

        return rmActionPack
    }
    def getCachedDefinitionAux(Definition definition) {

        def rmActionPack = getRecordmActionPack(definition)

        def newInstanceMsg = RecordmMsgBuilder.aMessage("cob-admin", definition, "update").newField(definition.getFirstField("Text"), "text")
                .build()

        AuditInstanceUpdater auditInstanceUpdated = new AuditInstanceUpdater(getRecordmActionPack(definition), null, null)

        return auditInstanceUpdated.getDefinitionFromCache(newInstanceMsg)
    }

    def "test cache different definition version"() {
        def cache = AuditInstanceUpdater.getCache()

        def definition = getDefinition().build()

        def cachedDefinition1 = getCachedDefinitionAux(definition)

        // second definition
        def definitionSecond = getDefinition().build()
        definitionSecond.version = 14

        def cachedDefinitionSecond = getCachedDefinitionAux(definitionSecond)
        expect:
        cache.size() == 1
        cachedDefinition1.version == definition.version
        cachedDefinitionSecond.version == definitionSecond.version
        cachedDefinition1.version != cachedDefinitionSecond.version
    }

    def mockInstanceMSG(){
        def newInstanceMsg = Mock(RecordmMsg.class)
        newInstanceMsg.getTimestamp() >> System.currentTimeMillis()
        newInstanceMsg.value("any value") >> null
        newInstanceMsg.action >> "add"
        newInstanceMsg.user >> "any user"
        newInstanceMsg.value("any value", Long.class) >> Long.valueOf(12000000000L)
        return newInstanceMsg
    }
}