package io.ast.maven.polyglot.toml;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

import org.apache.maven.model.*;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.io.ModelParseException;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.tuweni.toml.Toml;
import org.apache.tuweni.toml.TomlArray;
import org.apache.tuweni.toml.TomlTable;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;

@SuppressWarnings({"unused", "JavadocReference"})
@Component(role = ModelProcessor.class)
public class TomlModelProcessor implements ModelProcessor {

    @Requirement
    private ModelReader modelReader;
    private boolean isStrict;

    @Override
    public File locatePom(File projectDirectory) {
        var dir = projectDirectory.toPath();
        var pom = dir.resolve("pom.toml");
        if (!Files.exists(pom)) {
            pom = dir.resolve("pom.xml");
        }
        return pom.toFile();
    }

    @Override
    public Model read(File input, Map<String, ?> options) throws IOException, ModelParseException {
        try (FileInputStream inputStream = new FileInputStream(input)) {
            Model model = read(inputStream, options);
            model.setPomFile(input);
            return model;
        }
    }

    @Override
    public Model read(InputStream input, Map<String, ?> options) throws IOException, ModelParseException {
        return read(new InputStreamReader(input, Charset.defaultCharset()), options);
    }

    @Override
    public Model read(Reader input, Map<String, ?> options) throws IOException, ModelParseException {
        var file = getModelBuildSource(options);
        if (file != null && file.endsWith(".toml")) {
            isStrict = getModelIsStrict(options);
            return readToml(input);
        } else {
            // XML FILE ==> DefaultModelReader
            return modelReader.read(input, options);
        }
    }

    private boolean getModelIsStrict(Map<String, ?> options) {
        Object value = (options != null) ? options.get("org.apache.maven.model.io.isStrict") : "false";
        if (value instanceof String source) {
            return Boolean.parseBoolean(source);
        } else {
            return false;
        }
    }

    private String getModelBuildSource(Map<String, ?> options) {
        Object value = (options != null) ? options.get("org.apache.maven.model.building.source") : null;
        if (value instanceof FileModelSource source) {
            return source.getFile().toString();
        } else {
            return null;
        }
    }

    /**
     * @param input pom input reader
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseModel(XmlPullParser, boolean)
     */
    private Model readToml(Reader input) throws IOException, ModelParseException {
        var config = Toml.parse(input);

        var model = new Model();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "project":
                readTomlProject(model, config.getTable(key));
                break;
            case "parent":
                model.setParent(readTomlParent(config.getTable(key)));
                break;
            case "property":
            case "properties":
                for (var entry : readTomlProperties(config.getTable(key)).entrySet()) {
                    model.addProperty(entry.getKey(), entry.getValue());
                }
                break;
            case "scm":
                model.setScm(readTomlScm(config.getTable(key)));
                break;

            case "management":
                readTomlManagement(model, config.getTable(key));
                break;
            case "dependency":
                readTomlDependencies(model, config.getArray(key), null);
                break;
            case "dependencies":
                readTomlDependencies(model, config.getTable(key));
                break;
            case "directory":
            case "directories":
                model.setBuild(readTomlBuildDirectory(model.getBuild(), config.getTable(key)));
                break;
            case "repositories":
                model.setRepositories(readTomlRepositories(config.getArray(key)));
                break;
            case "pluginRepositories":
                model.setPluginRepositories(readTomlRepositories(config.getArray(key)));
                break;
            case "build":
                model.setBuild(readTomlBuild(model.getBuild(), config.getTable(key)));
                break;
            default:
                checkTag(key);
            }
        }

        return model;
    }

    /**
     * @param model  POM
     * @param config POM toml
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseModel(XmlPullParser, boolean)
     */
    private void readTomlProject(Model model, TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "project");

        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "parent":
                model.setParent(readTomlParent(config.getTable(key)));
                break;
            case "modelVersion":
                model.setModelVersion(config.getString(key));
                break;
            case "group":
            case "groupId":
                model.setGroupId(config.getString(key));
                break;
            case "artifact":
            case "artifactId":
                model.setArtifactId(config.getString(key));
                break;
            case "version":
                model.setVersion(config.getString(key));
                break;
            case "packaging":
                model.setPackaging(config.getString(key));
                break;
            case "name":
                model.setName(config.getString(key));
                break;
            case "description":
                model.setDescription(config.getString(key));
                break;
            case "url":
                model.setUrl(config.getString(key));
                break;
            case "inceptionYear":
                model.setInceptionYear(config.getString(key));
                break;
            case "organization":
                model.setOrganization(readTomlOrganization(config.getTable(key)));
                break;
            case "license":
            case "licenses":
                model.setLicenses(readTomlLicense(config.getArray(key)));
                break;
            case "developer":
                model.setDevelopers(append(model.getDevelopers(), readTomlDeveloper(config.getArray(key))));
                break;
            case "developers":
                model.setDevelopers(append(model.getDevelopers(), readTomlDevelopers(config.getArray(key))));
                break;
            case "contributor":
                model.setContributors(append(model.getContributors(), readTomlContributor(config.getArray(key))));
                break;
            case "contributors":
                model.setContributors(append(model.getContributors(), readTomlContributors(config.getArray(key))));
                break;
            case "mailingList":
            case "mailingLists":
                model.setMailingLists(readTomlMailingList(config.getArray(key)));
                break;
            case "prerequisites":
                model.setPrerequisites(readTomlPrerequisites(config.getTable(key)));
                break;
            case "module":
            case "modules":
                model.setModules(asStringList(config.get(key)));
                break;
            default:
                checkTag("project", key);
            }
        }
    }

    /**
     * @param config parent toml
     * @return Parent
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseParent(XmlPullParser, boolean)
     */
    private Parent readTomlParent(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "parent");

        var parent = new Parent();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "group":
            case "groupId":
                parent.setGroupId(config.getString(key));
                break;
            case "artifact":
            case "artifactId":
                parent.setArtifactId(config.getString(key));
                break;
            case "version":
                parent.setVersion(config.getString(key));
                break;
            case "relativePath":
                parent.setRelativePath(config.getString(key));
                break;
            default:
                checkTag("parent", key);
            }
        }
        return parent;
    }

    /**
     * @param config Organization toml
     * @return Organization
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseOrganization(XmlPullParser, boolean)
     */
    private Organization readTomlOrganization(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "organization");

        var organization = new Organization();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "name":
                organization.setName(config.getString(key));
                break;
            case "url":
                organization.setUrl(config.getString(key));
                break;
            default:
                checkTag("project.organization", key);
            }
        }

        return organization;
    }


    /**
     * @param config License toml array
     * @return list of License
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseLicense(XmlPullParser, boolean)
     */
    private List<License> readTomlLicense(TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config, "licenses");

        var ret = new ArrayList<License>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlLicense(config.getTable(i)));
        }
        return ret;
    }

    /**
     * @param config License toml
     * @return License
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseLicense(XmlPullParser, boolean)
     */
    private License readTomlLicense(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "license");

        var license = new License();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "name":
                license.setName(config.getString(key));
                break;
            case "url":
                license.setUrl(config.getString(key));
                break;
            case "distribution":
                license.setDistribution(config.getString(key));
                break;
            case "comments":
                license.setComments(config.getString(key));
                break;
            default:
                checkTag("project.license[" + license.getName() + "]", key);
            }
        }

        return license;
    }

    /**
     * <pre>
     *     [[project.developer]]
     *     name = '...'
     *     email = '...'
     * </pre>
     *
     * @param config Developer toml array
     * @return list of Developer
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseDeveloper(XmlPullParser, boolean)
     */
    private List<Developer> readTomlDeveloper(TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config, "developers");
        var ret = new ArrayList<Developer>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlContributor(new Developer(), config.getTable(i)));
        }
        return ret;
    }

    /**
     * <pre>
     *     [project]
     *     developers = [
     *      'Name',
     *      'Name &lt;Email&gt;',
     *      {name = '...', email = '...'}
     *     ]
     * </pre>
     *
     * @param config Developer toml array
     * @return list of Developer
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseDeveloper(XmlPullParser, boolean)
     */
    private List<Developer> readTomlDevelopers(TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config, "developers");
        var ret = new ArrayList<Developer>(config.size());
        for (int i = 0; i < config.size(); i++) {
            var content = config.get(i);
            if (content instanceof String developer) {
                ret.add(parseContributor(new Developer(), developer));
            } else if (content instanceof TomlTable developer) {
                ret.add(readTomlContributor(new Developer(), developer));
            } else {
                checkType("developer", "String|Table");
            }
        }
        return ret;
    }

    /**
     * <pre>
     *     [[project.contributor]]
     *     name = '...'
     *     email = '...'
     * </pre>
     *
     * @param config Contributor toml array
     * @return list of Contributor
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseContributor(XmlPullParser, boolean)
     */
    private List<Contributor> readTomlContributor(TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config, "contributors");
        var ret = new ArrayList<Contributor>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlContributor(new Contributor(), config.getTable(i)));
        }
        return ret;
    }

    /**
     * <pre>
     *     [project]
     *     contributors = [
     *      'Name',
     *      'Name &lt;Email&gt;',
     *      {name = '...', email = '...'}
     *     ]
     * </pre>
     *
     * @param config Contributor toml array
     * @return list of Contributor
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseContributor(XmlPullParser, boolean)
     */
    private List<Contributor> readTomlContributors(TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config, "contributors");
        var ret = new ArrayList<Contributor>(config.size());
        for (int i = 0; i < config.size(); i++) {
            var content = config.get(i);
            if (content instanceof String developer) {
                ret.add(parseContributor(new Contributor(), developer));
            } else if (content instanceof TomlTable developer) {
                ret.add(readTomlContributor(new Contributor(), developer));
            } else {
                throw new ModelParseException("Unrecognised 'contributor' value: " + content, -1, -1);
            }
        }
        return ret;
    }

    private <T extends Contributor> T parseContributor(T contributor, String content) throws ModelParseException {
        if (content.contains("<") && content.endsWith(">")) {
            var i = content.indexOf('<');
            var name = content.substring(0, i).strip();
            var email = content.substring(i + 1, content.length() - 1).strip();
            contributor.setName(name);
            contributor.setEmail(email);
        } else {
            contributor.setName(content);
        }
        return contributor;
    }

    /**
     * @param contributor Developer or Contributor instance
     * @param config Developer or Contributor toml
     * @param <T> type of {@code contributor}
     * @return {@code contributor}
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseContributor(XmlPullParser, boolean)
     * @see MavenXpp3Reader#parseDeveloper(XmlPullParser, boolean)
     */
    private <T extends Contributor> T readTomlContributor(T contributor, TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config);

        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "name":
                contributor.setName(config.getString(key));
                break;
            case "email":
                contributor.setEmail(config.getString(key));
                break;
            case "url":
                contributor.setUrl(config.getString(key));
                break;
            case "organization":
                contributor.setOrganization(config.getString(key));
                break;
            case "organizationUrl":
                contributor.setOrganizationUrl(config.getString(key));
                break;
            case "role":
            case "roles":
                contributor.setRoles(asStringList(config.get(key)));
                break;
            case "timezone":
                contributor.setTimezone(config.getString(key));
                break;
            case "properties":
                for (var entry : config.getTable(key).toMap().entrySet()) {
                    contributor.addProperty(entry.getKey(), (String) entry.getValue());
                }
                break;
            case "id":
                if (contributor instanceof Developer developer) {
                    developer.setId(config.getString(key));
                    break;
                }
            default:
                var role = (contributor instanceof Developer) ? "developer" : "contributor";
                checkTag("project." + role + "[" + contributor.getName() + "]", key);
            }
        }

        return contributor;
    }

    /**
     * @param config MailingList toml array
     * @return list of MailingList
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseMailingList(XmlPullParser, boolean)
     */
    private List<MailingList> readTomlMailingList(TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config, "mailingLists");
        var ret = new ArrayList<MailingList>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlMailingList(config.getTable(i)));
        }
        return ret;
    }

    /**
     * @param config MailingList toml
     * @return MailingList
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseMailingList(XmlPullParser, boolean)
     */
    private MailingList readTomlMailingList(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "mailingList");

        var mail = new MailingList();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "name":
                mail.setName(config.getString(key));
                break;
            case "subscribe":
                mail.setSubscribe(config.getString(key));
                break;
            case "unsubscribe":
                mail.setUnsubscribe(config.getString(key));
                break;
            case "post":
                mail.setPost(config.getString(key));
                break;
            case "archive":
                mail.setArchive(config.getString(key));
                break;
            case "other":
            case "archives":
            case "otherArchive":
            case "otherArchives":
                mail.setOtherArchives(asStringList(config.get(key)));
                break;
            default:
                checkTag("project.mailingList[" + mail.getName() + "]", key);
            }
        }

        return mail;
    }

    /**
     * @param config Prerequisites toml
     * @return Prerequisites
     * @throws ModelParseException
     * @see MavenXpp3Reader#parsePrerequisites(XmlPullParser, boolean)
     */
    private Prerequisites readTomlPrerequisites(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "prerequisites");

        var pre = new Prerequisites();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "maven":
                pre.setMaven(config.getString(key));
                break;
            default:
                checkTag("project.prerequisites", key);
            }
        }

        return pre;
    }

    /**
     * @param config toml property table
     * @return properties map
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseModel(XmlPullParser, boolean)
     */
    private Map<String, String> readTomlProperties(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config);

        var map = new HashMap<String, String>();
        for (var key : config.keySet()) {
            var value = config.get(key);
            if (value instanceof Boolean v) {
                map.put(key, v.toString());
            } else if (value instanceof String v) {
                map.put(key, v);
            } else if (value instanceof TomlTable table) {
                readTomlProperties(map, table, key);
            } else {
                checkTag("properties[" + key + "]", "Boolean|String|Number|Table");
            }
        }

        return map;
    }

    /**
     * @param map    properties map
     * @param config toml property  sub-table
     * @param p      parent key
     * @throws ModelParseException
     */
    private void readTomlProperties(Map<String, String> map, TomlTable config, String p) throws ModelParseException {
        for (var key : config.keySet()) {
            var value = config.get(key);
            if (value instanceof Boolean v) {
                map.put(p + "." + key, v.toString());
            } else if (value instanceof String v) {
                map.put(p + "." + key, v);
            } else if (value instanceof Integer v) {
                map.put(p + "." + key, v.toString());
            } else if (value instanceof Double v) {
                map.put(p + "." + key, v.toString());
            } else if (value instanceof TomlTable table) {
                readTomlProperties(map, table, p + "." + key);
            } else {
                checkType("properties[" + p + "." + key + "]", "Boolean|String|Number|Table");
            }
        }
    }

    /**
     *
     * @param config Scm toml
     * @return Scm
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseScm(XmlPullParser, boolean)
     */
    private Scm readTomlScm(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "scm");

        var scm = new Scm();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "child":
                for (var entry : readTomlProperties(config.getTable(key)).entrySet()) {
                    switch (entry.getKey()) {
                    case "scm.connection.inherit.append.path":
                        scm.setChildScmConnectionInheritAppendPath(entry.getValue());
                        break;
                    case "child.scm.developerConnection.inherit.append.path":
                        scm.setChildScmDeveloperConnectionInheritAppendPath(entry.getValue());
                        break;
                    case "child.scm.url.inherit.append.path":
                        scm.setChildScmUrlInheritAppendPath(entry.getValue());
                        break;
                    default:
                        checkAttribute("scm", entry.getKey());
                    }
                }
                break;
            case "connection":
                scm.setConnection(config.getString(key));
                break;
            case "developerConnection":
                scm.setDeveloperConnection(config.getString(key));
                break;
            case "tag":
                scm.setTag(config.getString(key));
                break;
            case "url":
                scm.setUrl(config.getString(key));
                break;
            default:
                checkTag("scm", key);
            }
        }
        return scm;
    }

    /**
     * <pre>
     *     [management]
     *     issue = {}
     *     ci = {}
     *     distribution = {}
     *     dependency = {}
     *     plugin = {}
     * </pre>
     *
     * @param model  POM
     * @param config Management toml
     * @throws ModelParseException
     */
    private void readTomlManagement(Model model, TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "management");

        var manager = new IssueManagement();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "issue":
                model.setIssueManagement(readTomlIssueManagement(config.getTable(key)));
                break;
            case "ci":
                model.setCiManagement(readTomlCiManagement(config.getTable(key)));
                break;
            case "distribution":
                model.setDistributionManagement(readTomlDistributionManagement(config.getTable(key)));
                break;
            case "dependency":
                model.setDependencyManagement(readTomlDependencyManager(config.getTable(key)));
                break;
            case "plugin":
            case "plugins":
                var build = model.getBuild();
                if (build == null) build = new Build();
                build.setPluginManagement(readTomlPluginManagement(config.getTable(key)));
                break;
            default:
                checkTag("management", key);
            }
        }
    }

    /**
     * @param config IssueManagement toml
     * @return IssueManagement
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseIssueManagement(XmlPullParser, boolean)
     */
    private IssueManagement readTomlIssueManagement(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "issueManagement");

        var manager = new IssueManagement();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "system":
                manager.setSystem(config.getString(key));
                break;
            case "url":
                manager.setUrl(config.getString(key));
                break;
            default:
                checkTag("issueManagement", key);
            }
        }
        return manager;
    }

    /**
     * @param config CiManagement toml
     * @return CiManagement
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseCiManagement(XmlPullParser, boolean)
     */
    private CiManagement readTomlCiManagement(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "ciManagement");

        var manager = new CiManagement();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "system":
                manager.setSystem(config.getString(key));
                break;
            case "url":
                manager.setUrl(config.getString(key));
                break;
            case "notifiers":
                manager.setNotifiers(readTomlNotifier(config.getArray(key)));
                break;
            default:
                checkTag("ciManagement", key);
            }
        }
        return manager;
    }

    /**
     * @param config Notifier toml array
     * @return list of Notifier
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseNotifiers(XmlPullParser, boolean)
     */
    private List<Notifier> readTomlNotifier(TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config, "notifiers");

        var ret = new ArrayList<Notifier>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlNotifier(config.getTable(i)));
        }

        return ret;
    }

    /**
     * @param config Notifier toml
     * @return Notifier
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseNotifiers(XmlPullParser, boolean)
     */
    private Notifier readTomlNotifier(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "notifier");

        var not = new Notifier();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "type":
                not.setType(config.getString(key));
                break;
            case "onError":
            case "sendOnError":
                not.setSendOnError(config.getBoolean(key));
                break;
            case "onFailure":
            case "sendOnFailure":
                not.setSendOnFailure(config.getBoolean(key));
                break;
            case "onSuccess":
            case "sendOnSuccess":
                not.setSendOnSuccess(config.getBoolean(key));
                break;
            case "onWarning":
            case "sendOnWarning":
                not.setSendOnWarning(config.getBoolean(key));
                break;
            case "address":
                not.setAddress(config.getString(key));
                break;
            case "configuration":
                for (var entry : readTomlProperties(config.getTable(key)).entrySet()) {
                    not.addConfiguration(entry.getKey(), entry.getValue());
                }
                break;
            default:
                checkTag("ciManagement.notifier[" + not.getType() + "]", key);
            }
        }
        return not;
    }

    /**
     * @param config DistributionManagement toml
     * @return DistributionManagement
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseDistributionManagement(XmlPullParser, boolean)
     */
    private DistributionManagement readTomlDistributionManagement(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "distributionManagement");

        var manager = new DistributionManagement();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "repository":
                manager.setRepository(readTomlDeploymentRepository(config.getTable(key)));
                break;
            case "snapshotRepository":
                manager.setSnapshotRepository(readTomlDeploymentRepository(config.getTable(key)));
                break;
            case "site":
                manager.setSite(readTomlSite(config.getTable(key)));
                break;
            case "downloadUrl":
                manager.setDownloadUrl(config.getString(key));
                break;
            case "relocation":
                manager.setRelocation(readTomlRelocation(config.getTable(key)));
                break;
            case "status":
                manager.setStatus(config.getString(key));
                break;
            default:
                checkTag("distributionManagement", key);
            }
        }
        return manager;
    }

    /**
     * @param config DeploymentRepository toml
     * @return DeploymentRepository
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseDeploymentRepository(XmlPullParser, boolean)
     */
    private DeploymentRepository readTomlDeploymentRepository(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "repository");

        var repo = new DeploymentRepository();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "uniqueVersion":
                repo.setUniqueVersion(config.getBoolean(key));
                break;
            case "releases":
                repo.setReleases(readTomlRepositoryPolicy(repo.getName(), "releases", config.getTable(key)));
                break;
            case "snapshots":
                repo.setSnapshots(readTomlRepositoryPolicy(repo.getName(), "snapshots", config.getTable(key)));
                break;
            case "id":
                repo.setId(config.getString(key));
                break;
            case "name":
                repo.setName(config.getString(key));
                break;
            case "url":
                repo.setUrl(config.getString(key));
                break;
            case "layout":
                repo.setLayout(config.getString(key));
                break;
            default:
                checkTag("repository[" + repo.getName() + "]", key);
            }
        }
        return repo;
    }


    /**
     * @param config Site toml
     * @return Site
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseSite(XmlPullParser, boolean)
     */
    private Site readTomlSite(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "site");

        var site = new Site();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "child":
                for (var entry : readTomlProperties(config.getTable(key)).entrySet()) {
                    switch (entry.getKey()) {
                    case "site.url.inherit.append.path":
                        site.setChildSiteUrlInheritAppendPath(entry.getValue());
                        break;
                    default:
                        checkAttribute("site", entry.getKey());
                    }
                }
                break;
            case "id":
                site.setId(config.getString(key));
                break;
            case "name":
                site.setName(config.getString(key));
                break;
            case "url":
                site.setUrl(config.getString(key));
                break;
            default:
                checkTag("site", key);
            }
        }
        return site;
    }

    /**
     * @param config toml
     * @return Relocation
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseRelocation(XmlPullParser, boolean)
     */
    private Relocation readTomlRelocation(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "relocation");

        var loc = new Relocation();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "group":
            case "groupId":
                loc.setGroupId(config.getString(key));
                break;
            case "artifact":
            case "artifactId":
                loc.setArtifactId(config.getString(key));
                break;
            case "version":
                loc.setVersion(config.getString(key));
                break;
            case "message":
                loc.setMessage(config.getString(key));
                break;
            default:
                checkTag("relocation", key);
            }
        }

        return loc;
    }

    /**
     * @param config DependencyManagement toml
     * @return DependencyManagement
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseDependencyManagement(XmlPullParser, boolean)
     */
    private DependencyManagement readTomlDependencyManager(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "dependencyManager");

        var manager = new DependencyManagement();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "dependencies":
                manager.setDependencies(readTomlDependencies(config.getArray(key), null));
                break;
            default:
                checkTag("dependencyManagement", key);
            }
        }

        return manager;
    }

    /**
     * <pre>
     * [[dependency]]
     * group = '...'
     * artifact = '...'
     * version = '...'
     * </pre>
     *
     * @param model  POM
     * @param config Dependency toml array
     * @param scope Dependency scope, optional.
     * @throws ModelParseException
     */
    private void readTomlDependencies(Model model, TomlArray config, String scope) throws ModelParseException {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(config, "dependencies");

        model.setDependencies(append(model.getDependencies(), readTomlDependencies(config, scope)));
    }

    /**
     * <pre>
     * [dependencies]
     * compile = [
     *      'group:artifact:version',
     *      {group = '...', artifact = '...', version = '...'},
     * ]
     * </pre>
     *
     * <pre>
     * [dependencies.test]
     * "group:artifact" = 'version'
     * "group:artifact" = {version = '...'}
     * </pre>
     *
     * @param model  POM
     * @param config Dependency toml array
     * @param scope Dependency scope, optional.
     * @throws ModelParseException
     */
    private void readTomlDependencies(Model model, TomlTable config) throws ModelParseException {
        Objects.requireNonNull(model, "mode");
        Objects.requireNonNull(config, "dependencies");

        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "provider":
            case "system":
            case "compile":
            case "runtime":
            case "test":
                if (config.isArray(key)) {
                    model.setDependencies(append(model.getDependencies(), readTomlDependencies(config.getArray(key), key)));
                } else if (config.isTable(key)) {
                    model.setDependencies(append(model.getDependencies(), readTomlDependencies(config.getTable(key), key)));
                } else {
                    checkTag("dependency", key);
                }
                break;
            default:
                checkTag("dependency", key);
            }
        }
    }

    /**
     * @param config Dependencies toml array, contains dependency expr or dependency table.
     * @param scope Dependency scope, optional.
     * @return list of Dependency
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseDependency(XmlPullParser, boolean)
     */
    private List<Dependency> readTomlDependencies(TomlArray config, String scope) throws ModelParseException {
        Objects.requireNonNull(config, "dependencies");

        var ret = new ArrayList<Dependency>(config.size());
        for (int i = 0; i < config.size(); i++) {
            var child = config.get(i);
            if (child instanceof String table) {
                ret.add(parseDependencyName(table, null, scope));
            } else if (child instanceof TomlTable table) {
                ret.add(readTomlDependency(table, scope));
            } else {
                checkTag("dependency");
            }
        }

        return ret;
    }

    /**
     * @param config Dependencies toml table contains dependency expr ("group:artifact") map to version or a table.
     * @param scope Dependency scope, optional.
     * @return list of Dependency
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseDependency(XmlPullParser, boolean)
     */
    private List<Dependency> readTomlDependencies(TomlTable config, String scope) throws ModelParseException {
        Objects.requireNonNull(config, "dependencies");

        var ret = new ArrayList<Dependency>(config.size());
        for (var key : config.keySet()) {
            ret.add(parseDependencyName(key, config.get(List.of(key)), scope));
        }

        return ret;
    }


    /**
     * @param config Dependency toml
     * @param scope Dependency scope, optional.
     * @return list of Dependency
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseDependency(XmlPullParser, boolean)
     */
    private Dependency readTomlDependency(TomlTable config, String scope) throws ModelParseException {
        Objects.requireNonNull(config, "dependency");

        var dep = new Dependency();
        if (scope != null) {
            dep.setScope(scope);
        }

        return readTomlDependency(dep, config);
    }

    /**
     *
     * @param line Dependency expression, example "group[:artifact[:version]]"
     * @param config ({@code String}) version, or ({@link TomlTable}) table.
     * @param scope Dependency scope, optional.
     * @return Dependency
     * @throws ModelParseException
     */
    private Dependency parseDependencyName(String line, Object config, String scope) throws ModelParseException {
        Objects.requireNonNull(line, "dependency");

        var dep = new Dependency();
        if (scope != null) {
            dep.setScope(scope);
        }

        // group:artifact:version
        // group[:artifact] = version | {...}
        var parts = line.split(":");
        switch (parts.length) {
        case 3:
            dep.setVersion(parts[2]);
        case 2:
            dep.setArtifactId(parts[1]);
        case 1:
            dep.setGroupId(parts[0]);
            break;
        default:
            throw new ModelParseException("Unrecognised tag: 'dependency." + line + "'", -1, -1);
        }

        if (config != null) {
            if (config instanceof String version) {
                dep.setVersion(version);
            } else if (config instanceof TomlTable table) {
                readTomlDependency(dep, table);
            } else {
                checkType(line, "String|Table");
            }
        }
        return dep;
    }

    /**
     * @param dep Dependency
     * @param config Dependency table
     * @return {@code dep}
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseDependency(XmlPullParser, boolean)
     */
    private Dependency readTomlDependency(Dependency dep, TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "dependency");
        Objects.requireNonNull(dep, "dependency");

        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "group":
            case "groupId":
                dep.setGroupId(config.getString(key));
                break;
            case "artifact":
            case "artifactId":
                dep.setArtifactId(config.getString(key));
                break;
            case "version":
                dep.setVersion(config.getString(key));
                break;
            case "type":
                dep.setType(config.getString(key));
                break;
            case "classifier":
                dep.setClassifier(config.getString(key));
                break;
            case "scope":
                dep.setScope(config.getString(key));
                break;
            case "systemPath":
                dep.setSystemPath(config.getString(key));
                break;
            case "optional":
                dep.setOptional(config.getBoolean(key));
                break;
            case "exclusions":
                dep.setExclusions(readTomlExclusion(dep, config.getArray(key)));
                break;
            default:
                checkTag("dependency[" + dep.getGroupId() + ":" + dep.getArtifactId() + "]", key);
            }
        }

        return dep;
    }

    /**
     * @param dep Dependency belongs to
     * @param config Exclusion toml array
     * @return list of Exclusion
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseExclusion(XmlPullParser, boolean)
     */
    private List<Exclusion> readTomlExclusion(Dependency dep, TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config, "exclusions");

        var ret = new ArrayList<Exclusion>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlExclusion(dep, config.getTable(i)));
        }

        return ret;
    }

    /**
     * @param dep Dependency belongs to
     * @param config Exclusion toml
     * @return Exclusion
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseExclusion(XmlPullParser, boolean)
     */
    private Exclusion readTomlExclusion(Dependency dep, TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "exclusion");

        var ext = new Exclusion();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "group":
            case "groupId":
                ext.setGroupId(config.getString(key));
                break;
            case "artifact":
            case "artifactId":
                ext.setArtifactId(config.getString(key));
                break;
            default:
                var depName = dep.getGroupId() + ":" + dep.getArtifactId();
                var name = ext.getGroupId() + ":" + ext.getArtifactId();
                checkTag("dependency[" + depName + "].exclusion[" + name + "]", key);
            }
        }

        return ext;
    }


    /**
     * @param config Repository toml array
     * @return list of Repository
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseRepository(XmlPullParser, boolean)
     */
    private List<Repository> readTomlRepositories(TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config, "repositories");

        var ret = new ArrayList<Repository>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlRepositories(config.getTable(i)));
        }

        return ret;
    }

    /**
     * @param config Repository toml
     * @return Repository
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseRepository(XmlPullParser, boolean)
     */
    private Repository readTomlRepositories(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "repository");

        var rep = new Repository();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "releases":
                rep.setReleases(readTomlRepositoryPolicy(rep.getName(), key, config.getTable(key)));
                break;
            case "snapshots":
                rep.setSnapshots(readTomlRepositoryPolicy(rep.getName(), key, config.getTable(key)));
                break;
            case "id":
                rep.setId(config.getString(key));
                break;
            case "name":
                rep.setName(config.getString(key));
                break;
            case "url":
                rep.setUrl(config.getString(key));
                break;
            case "layout":
                rep.setLayout(config.getString(key));
                break;
            default:
                checkTag("repository[" + rep.getName() + "]", key);
            }
        }

        return rep;
    }

    /**
     * @param repository The name of the Repository belongs to
     * @param tag current tag, either {@code "releases"} or {@code "snapshots"}
     * @param config RepositoryPolicy toml
     * @return RepositoryPolicy
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseRepositoryPolicy(XmlPullParser, boolean)
     */
    private RepositoryPolicy readTomlRepositoryPolicy(String repository, String tag, TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "repositoryPolicy");

        var rep = new RepositoryPolicy();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "enabled":
                rep.setEnabled(config.getBoolean(key));
                break;
            case "updatePolicy":
                rep.setUpdatePolicy(config.getString(key));
                break;
            case "checksumPolicy":
                rep.setChecksumPolicy(config.getString(key));
                break;
            default:
                checkTag("repository[" + repository + "]." + tag, key);
            }
        }

        return rep;
    }

    /**
     * @param build Pom build
     * @param config Build toml
     * @return Build
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseBuild(XmlPullParser, boolean)
     */
    private Build readTomlBuild(Build build, TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "build");

        if (build == null) build = new Build();

        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "sourceDirectory":
                System.out.println("use '[directory] source=...' instead");
                build.setSourceDirectory(config.getString(key));
                break;
            case "scriptSourceDirectory":
                System.out.println("use '[directory] script-source=...' instead");
                build.setScriptSourceDirectory(config.getString(key));
                break;
            case "testSourceDirectory":
                System.out.println("use '[directory] test-source=...' instead");
                build.setTestSourceDirectory(config.getString(key));
                break;
            case "outputDirectory":
                System.out.println("use '[directory] output=...' instead");
                build.setOutputDirectory(config.getString(key));
                break;
            case "testOutputDirectory":
                System.out.println("use '[directory] test-output=...' instead");
                build.setTestOutputDirectory(config.getString(key));
                break;
            case "extension":
            case "extensions":
                build.setExtensions(readTomlExtension(config.getArray(key)));
                break;
            case "defaultGoal":
                build.setDefaultGoal(config.getString(key));
                break;
            case "resource":
            case "resources":
                System.out.println("use '[[directory.resource]]' instead");
                build.setResources(readTomlResource(config.getArray(key)));
                break;
            case "testResource":
            case "testResources":
                System.out.println("use '[[directory.test-resource]]' instead");
                build.setTestResources(readTomlResource(config.getArray(key)));
                break;
            case "directory":
                build.setDirectory(config.getString(key));
                break;
            case "finalName":
                build.setFinalName(config.getString(key));
                break;
            case "filter":
            case "filters":
                build.setFilters(asStringList(config.get(key)));
                break;
            case "plugin":
            case "plugins":
                build.setPlugins(readTomlPlugins(config.get(key)));
                break;
            case "pluginManagement":
                System.out.println("use '[management.plugin]' instead");
                build.setPluginManagement(readTomlPluginManagement(config.getTable(key)));
                break;
            default:
                checkTag("build", key);
            }
        }

        return build;
    }

    /**
     * <pre>
     * [directory]
     * source = '...'
     * test-source = '...'
     * </pre>
     * @param build POM Build
     * @param config Directory toml
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseBuild(XmlPullParser, boolean)
     */
    private Build readTomlBuildDirectory(Build build, TomlTable config) throws ModelParseException {
        if (build == null) build = new Build();

        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "source":
                build.setSourceDirectory(config.getString(key));
                break;
            case "scriptSource":
                build.setScriptSourceDirectory(config.getString(key));
                break;
            case "testSource":
                build.setTestSourceDirectory(config.getString(key));
                break;
            case "output":
                build.setOutputDirectory(config.getString(key));
                break;
            case "testOutput":
                build.setTestOutputDirectory(config.getString(key));
                break;
            case "resource":
            case "resources":
                build.setResources(readTomlResource(config.getArray(key)));
                break;
            case "testResource":
            case "testResources":
                build.setTestResources(readTomlResource(config.getArray(key)));
                break;
            default:
                checkTag("build", key);
            }
        }

        return build;
    }

    /**
     * @param config Extension toml array
     * @return list of Extension
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseExtension(XmlPullParser, boolean)
     */
    private List<Extension> readTomlExtension(TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config, "extensions");
        var ret = new ArrayList<Extension>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlExtension(config.getTable(i)));
        }
        return ret;
    }

    /**
     * @param config Extension toml
     * @return Extension
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseExtension(XmlPullParser, boolean)
     */
    private Extension readTomlExtension(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "extension");

        var ext = new Extension();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "group":
            case "groupId":
                ext.setGroupId(config.getString(key));
                break;
            case "artifact":
            case "artifactId":
                ext.setArtifactId(config.getString(key));
                break;
            case "version":
                ext.setVersion(config.getString(key));
                break;
            default:
                checkTag("extension[" + ext.getGroupId() + ":" + ext.getArtifactId() + "]", key);
            }
        }

        return ext;
    }

    /**
     * @param config Resource toml array
     * @return list of Resource
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseResource(XmlPullParser, boolean)
     */
    private List<Resource> readTomlResource(TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config, "resources");
        var ret = new ArrayList<Resource>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlResource(config.getTable(i)));
        }
        return ret;
    }

    /**
     * @param config Resource toml
     * @return Resource
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseResource(XmlPullParser, boolean)
     */
    private Resource readTomlResource(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "resource");

        var res = new Resource();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "targetPath":
                res.setTargetPath(config.getString(key));
                break;
            case "filtering":
                res.setFiltering(config.getBoolean(key));
                break;
            case "include":
            case "includes":
                res.setIncludes(asStringList(config.get(key)));
                break;
            case "exclude":
            case "excludes":
                res.setExcludes(asStringList(config.get(key)));
                break;
            default:
                checkTag("resource", key);
            }
        }

        return res;
    }

    /**
     * @param config PluginManagement toml
     * @return PluginManagement
     * @throws ModelParseException
     * @see MavenXpp3Reader#parsePluginManagement(XmlPullParser, boolean)
     */
    private PluginManagement readTomlPluginManagement(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "pluginManagement");

        var manager = new PluginManagement();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "plugins":
                manager.setPlugins(readTomlPlugins(config.getArray(key)));
                break;
            default:
                checkTag("pluginManagement", key);
            }
        }

        return manager;
    }

    /**
     * <pre>
     *     [[build.plugin]]
     *     group = '...'
     *     artifact = '..'
     *     version = '...'
     *     configuration = '...'
     * </pre>
     * <pre>
     *     [build.plugin."group:artifact:version"]
     *     configuration = '...'
     * </pre>
     *
     * @param config Plugin toml array or table
     * @return list of Plugin
     * @throws ModelParseException
     */
    private List<Plugin> readTomlPlugins(Object config) throws ModelParseException {
        Objects.requireNonNull(config, "plugins");

        if (config instanceof TomlArray array) {
            return readTomlPlugins(array);

        } else if (config instanceof TomlTable table) {
            var ret = new ArrayList<Plugin>(table.size());
            for (var key : table.keySet()) {
                var plugin = new Plugin();
                var parts = key.split(":");
                plugin.setGroupId(parts[0]);
                plugin.setArtifactId(parts[1]);

                if (parts.length == 3) {
                    plugin.setVersion(parts[2]);
                } else if (parts.length > 3) {
                    throw new IllegalArgumentException("");
                }

                var pluginConfig = table.getTable(List.of(key));
                for (var subkey : pluginConfig.keySet()) {
                    readTomlPlugin(plugin, pluginConfig, subkey);
                }

                ret.add(plugin);
            }
            return ret;
        } else {
            checkType("build.plugin", "Array|Table");
            return List.of();
        }
    }

    /**
     * <pre>
     *     [[build.plugin]]
     *     group = '...'
     *     artifact = '..'
     *     version = '...'
     *     configuration = '...'
     * </pre>
     *
     * @param config Plugin toml array
     * @return list of Plugin
     * @throws ModelParseException
     * @see MavenXpp3Reader#parsePlugin(XmlPullParser, boolean)
     */
    private List<Plugin> readTomlPlugins(TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config, "plugins");
        var ret = new ArrayList<Plugin>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlPlugin(config.getTable(i)));
        }
        return ret;
    }


    /**
     * @param config Plugin toml
     * @return Plugin
     * @throws ModelParseException
     * @see MavenXpp3Reader#parsePlugin(XmlPullParser, boolean)
     */
    private Plugin readTomlPlugin(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "plugin");

        var plugin = new Plugin();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "group":
            case "groupId":
                plugin.setGroupId(config.getString(key));
                break;
            case "artifact":
            case "artifactId":
                plugin.setArtifactId(config.getString(key));
                break;
            default:
                readTomlPlugin(plugin, config, key);
            }
        }

        return plugin;
    }

    /**
     * set {@code plugin}.
     *
     * @param plugin current Plugin
     * @param config Plugin toml
     * @param key current property
     * @return {@code plugin}
     * @throws ModelParseException
     * @see MavenXpp3Reader#parsePlugin(XmlPullParser, boolean)
     */
    private Plugin readTomlPlugin(Plugin plugin, TomlTable config, String key) throws ModelParseException {
        Objects.requireNonNull(config, "plugin");

        switch (toCamelCase(key)) {
        case "version":
            plugin.setVersion(config.getString(key));
            break;
        case "extensions":
            plugin.setExtensions(config.getBoolean(key));
            break;
        case "executions":
            plugin.setExecutions(readTomlPluginExecutions(plugin, config.getArray(key)));
            break;
        case "dependency":
        case "dependencies":
            plugin.setDependencies(readTomlDependencies(config.getArray(key), null));
            break;
        case "goal":
        case "goals":
            plugin.setGoals(asDOM("goals", config.getTable(key)));
            break;
        case "inherited":
            plugin.setInherited(config.getBoolean(key));
            break;
        case "configuration":
            plugin.setConfiguration(asDOM("configuration", config.getTable(key)));
            break;
        default:
            checkTag("plugin[" + plugin.getGroupId() + ":" + plugin.getArtifactId() + "]", key);
        }

        return plugin;
    }

    /**
     * @param plugin current plugin
     * @param config PluginExecution toml array
     * @return list of PluginExecution
     * @throws ModelParseException
     * @see MavenXpp3Reader#parsePluginExecution(XmlPullParser, boolean)
     */
    private List<PluginExecution> readTomlPluginExecutions(Plugin plugin, TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config, "pluginExecutions");
        var ret = new ArrayList<PluginExecution>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlPluginExecutions(plugin, config.getTable(i)));
        }
        return ret;
    }

    /**
     * @param plugin current plugin
     * @param config PluginExecution toml
     * @return PluginExecution
     * @throws ModelParseException
     * @see MavenXpp3Reader#parsePluginExecution(XmlPullParser, boolean)
     */
    private PluginExecution readTomlPluginExecutions(Plugin plugin, TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "pluginExecution");

        var exe = new PluginExecution();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "id":
                exe.setId(config.getString(key));
                break;
            case "phase":
                exe.setPhase(config.getString(key));
                break;
            case "goal":
            case "goals":
                exe.setGoals(asStringList(config.get(key)));
                break;
            case "inherited":
                exe.setInherited(config.getBoolean(key));
                break;
            case "configuration":
                exe.setConfiguration(asDOM("configuration", config.getTable(key)));
                break;
            default:
                checkTag("plugin[" + plugin.getGroupId() + ":" + plugin.getArtifactId() + "].execution", key);
            }
        }

        return exe;
    }

    private List<String> asStringList(Object obj) {
        if (obj instanceof String s) {
            return List.of(s);
        } else if (obj instanceof TomlArray array) {
            return array.toList().stream().map(Object::toString).toList();
        } else {
            return null;
        }
    }

    /**
     * transform toml table to dom object.
     */
    private Xpp3Dom asDOM(String name, TomlTable config) throws ModelParseException {
        var dom = new Xpp3Dom(name);
        for (var key : config.keySet()) {
            if (config.isString(key)) {
                var child = new Xpp3Dom(toCamelCase(key));
                child.setValue(config.getString(key));
                dom.addChild(child);
            } else if (config.isBoolean(key)) {
                var child = new Xpp3Dom(toCamelCase(key));
                child.setValue(config.getBoolean(key).toString());
                dom.addChild(child);
            } else if (config.isTable(key)) {
                dom.addChild(asDOM(toCamelCase(key), config.getTable(key)));
            } else if (config.isArray(key)) {
                var list = toCamelCase(key);
                String item;
                if (list.endsWith("s")) {
                    item = list.substring(0, list.length() - 1);
                } else {
                    item = list;
                    list = list + "s";
                }

                var array = config.getArray(key);
                var child = new Xpp3Dom(list);
                for (int i = 0; i < array.size(); i++) {
                    child.addChild(asDOM(item, array.getTable(i)));
                }
                dom.addChild(child);
            } else {
                checkTag(name, key);
            }
        }
        return dom;
    }

    private static String toCamelCase(String key) {
        if (!key.contains("-")) {
            return key;
        }

        var length = key.length();
        var buffer = new StringBuilder(length);
        int i = 0;
        int j;
        while (i < length && (j = key.indexOf('-', i)) != -1) {
            buffer.append(key, i, j);
            if (j + 1 < length) {
                buffer.append(Character.toUpperCase(key.charAt(j + 1)));
            }
            i = j + 2;
        }

        if (i < length) {
            buffer.append(key, i, length);
        }

        return buffer.toString();
    }

    private static <T> List<T> append(List<T> old, List<T> add) {
        if (old == null) old = new ArrayList<>();
        old.addAll(add);
        return old;
    }

    private void checkAttribute(String key, String attr) throws ModelParseException {
        var message = "Unrecognised attribute: '" + key + "." + attr + "'";
        if (isStrict) {
            throw new ModelParseException(message, -1, -1);
        } else {
            System.out.println(message);
        }
    }

    private void checkTag(String tag) throws ModelParseException {
        var message = "Unrecognised tag: '" + tag + "'";
        if (isStrict) {
            throw new ModelParseException(message, -1, -1);
        } else {
            System.out.println(message);
        }
    }

    private void checkTag(String key, String tag) throws ModelParseException {
        var message = "Unrecognised tag: '" + key + "." + tag + "'";
        if (isStrict) {
            throw new ModelParseException(message, -1, -1);
        } else {
            System.out.println(message);
        }
    }

    private void checkType(String key, String type) throws ModelParseException {
        var message = "Expect tag type " + type + " for '" + key + "'";
        if (isStrict) {
            throw new ModelParseException(message, -1, -1);
        } else {
            System.out.println(message);
        }
    }
}
