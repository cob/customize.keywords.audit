import com.cultofbits.customizations.audit.AuditInstanceUpdater
import groovy.transform.Field

@Field def auditInstanceUpdated = new AuditInstanceUpdater(recordm, userm, log)

if (msg.user == "integrationm") return

if (msg.product == "recordm" && msg.action =~ "add|update") {
    def auditFields = auditInstanceUpdated.getAuditFields(msg)
    def updatedFields = auditInstanceUpdated.getAuditFieldsUpdates(auditFields, msg)

    if (updatedFields.size() > 0) {
        log.info("[_\$audit] Updating audit fields for instance ${msg.instance.id} updatedFields=${updatedFields}")
        recordm.update(msg.type, msg.instance.id, updatedFields);
    }
}