# customize.keyword.audit

* Depends on IntegrationM 14.3.0

## Install

```bash
cob-cli customize audit
```

## How to use:

```
Fields:
    field:
        name: Created By
        description: $audit.create.username
```

For more information you can consult [this link](https://learning.cultofbits.com/docs/cob-platform/admins/managing-information/available-customizations/calc/)

## Build & test

```bash
./run-tests.sh
```

## Release

1. Update `costumize.js` and increment version
2. git commit && git push