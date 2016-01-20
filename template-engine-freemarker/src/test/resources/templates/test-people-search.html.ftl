[#ftl]

[#-- @ftlvariable name="" type="uy.kohesive.kovert.template.freemarker.test.PeopleResults" --]

<html>
<body>
[#list persons as person]
    <div>${person.name}: ${person.age}</div>
[/#list]
</body>
</html>