package io.ast.maven.polyglot.toml;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
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
import org.sonatype.maven.polyglot.io.ModelReaderSupport;

@SuppressWarnings({"unused", "JavadocReference", "JavadocDeclaration"})
@Component(role = ModelProcessor.class)
public class TomlModelProcessor extends ModelReaderSupport implements ModelProcessor {

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
     * @param model
     * @param config
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
            case "properties":
                for (var entry : readTomlProperties(config.getTable(key)).entrySet()) {
                    model.addProperty(entry.getKey(), entry.getValue());
                }
                break;
            case "scm":
                model.setScm(readTomlScm(config.getTable(key)));
                break;
            case "issue":
            case "issueManagement":
                model.setIssueManagement(readTomlIssueManagement(config.getTable(key)));
                break;
            case "ci":
            case "ciManagement":
                model.setCiManagement(readTomlCiManagement(config.getTable(key)));
                break;
            case "distribution":
            case "distributionManagement":
                model.setDistributionManagement(readTomlDistributionManagement(config.getTable(key)));
                break;
            case "dependencyManagement":
                model.setDependencyManagement(readTomlDependencyManager(config.getTable(key)));
                break;
            case "repositories":
                model.setDependencies(readTomlDependencies(config.getArray(key)));
                break;
            case "pluginRepositories":
                model.setPluginRepositories(readTomlPluginsRepositories(config.getArray(key)));
                break;
            case "build":
                model.setBuild(readTomlBuild(config.getTable(key)));
                break;
            default:
                checkTag(key);
            }
        }

        return model;
    }

    /**
     * @param model
     * @param config
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
            case "groupId":
                model.setGroupId(config.getString(key));
                break;
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
            case "developers":
                model.setDevelopers(readTomlDeveloper(config.getArray(key)));
                break;
            case "contributor":
            case "contributors":
                model.setContributors(readTomlContributor(config.getArray(key)));
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
     * @param config
     * @return
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseParent(XmlPullParser, boolean)
     */
    private Parent readTomlParent(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "parent");

        var parent = new Parent();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "groupId":
                parent.setGroupId(config.getString(key));
                break;
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
     * @param config
     * @return
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
     * @param config
     * @return
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
     * @param config
     * @return
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
     * @param config
     * @return
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
     * @param config
     * @return
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
     * @param config
     * @return
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
     * @param config
     * @return
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
     * @param config
     * @return
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
     * @param config
     * @return
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
     * @param model
     * @param config
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseModel(XmlPullParser, boolean)
     */
    private Map<String, String> readTomlProperties(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config);

        var map = new HashMap<String, String>();
        for (var key : config.keySet()) {
            var value = config.get(key);
            if (value instanceof String v) {
                map.put(key, v);
            } else if (value instanceof TomlTable table) {
                readTomlProperties(map, table, key);
            } else {
                checkTag("properties", key);
            }
        }

        return map;
    }

    private void readTomlProperties(Map<String, String> map, TomlTable config, String p) throws ModelParseException {
        for (var key : config.keySet()) {
            var value = config.get(key);
            if (value instanceof String v) {
                map.put(p + "." + key, v);
            } else if (value instanceof TomlTable table) {
                readTomlProperties(map, table, p + "." + key);
            } else {
                checkTag("properties", p + "." + key);
            }
        }
    }

    /**
     * @param model
     * @param config
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
     * @param model
     * @param config
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
     * @param model
     * @param config
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
     * @param model
     * @param config
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
     * @param model
     * @param config
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
            case "sendOnError":
                not.setSendOnError(config.getBoolean(key));
                break;
            case "sendOnFailure":
                not.setSendOnFailure(config.getBoolean(key));
                break;
            case "sendOnSuccess":
                not.setSendOnSuccess(config.getBoolean(key));
                break;
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
     * @param model
     * @param config
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
     * @param model
     * @param config
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
     * @param model
     * @param config
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
     * @param model
     * @param config
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseRelocation(XmlPullParser, boolean)
     */
    private Relocation readTomlRelocation(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "relocation");

        var loc = new Relocation();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "groupId":
                loc.setGroupId(config.getString(key));
                break;
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
     * @param model
     * @param config
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseDependencyManagement(XmlPullParser, boolean)
     */
    private DependencyManagement readTomlDependencyManager(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "dependencyManager");

        var manager = new DependencyManagement();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "dependencies":
                manager.setDependencies(readTomlDependencies(config.getArray(key)));
                break;
            default:
                checkTag("dependencyManagement", key);
            }
        }

        return manager;
    }

    /**
     * @param model
     * @param config
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseDependency(XmlPullParser, boolean)
     */
    private List<Dependency> readTomlDependencies(TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config, "dependencies");

        var ret = new ArrayList<Dependency>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlDependency(config.getTable(i)));
        }

        return ret;
    }

    /**
     * @param model
     * @param config
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseDependency(XmlPullParser, boolean)
     */
    private Dependency readTomlDependency(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "dependency");

        var dep = new Dependency();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "groupId":
                dep.setGroupId(config.getString(key));
                break;
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
                System.out.println("Unsupported tag now: 'dependency." + key + "'");
                break;
            default:
                checkTag("dependency[" + dep.getGroupId() + ":" + dep.getArtifactId() + "]", key);
            }
        }

        return dep;
    }

    /**
     * @param model
     * @param config
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseRepository(XmlPullParser, boolean)
     */
    private List<Repository> readTomlPluginsRepositories(TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config, "pluginsRepositories");

        var ret = new ArrayList<Repository>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlRepositories(config.getTable(i)));
        }

        return ret;
    }

    /**
     * @param model
     * @param config
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
     * @param model
     * @param config
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
     * @param model
     * @param config
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseBuild(XmlPullParser, boolean)
     */
    private Build readTomlBuild(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "build");

        var build = new Build();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "sourceDirectory":
                build.setSourceDirectory(config.getString(key));
                break;
            case "scriptSourceDirectory":
                build.setScriptSourceDirectory(config.getString(key));
                break;
            case "testSourceDirectory":
                build.setTestSourceDirectory(config.getString(key));
                break;
            case "outputDirectory":
                build.setOutputDirectory(config.getString(key));
                break;
            case "testOutputDirectory":
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
                build.setResources(readTomlResource(config.getArray(key)));
                break;
            case "testResource":
            case "testResources":
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
                build.setPlugins(readTomlPlugins(config.getArray(key)));
                break;
            case "pluginManagement":
                build.setPluginManagement(readTomlPluginManagement(config.getTable(key)));
                break;
            default:
                checkTag("build", key);
            }
        }

        return build;
    }

    /**
     * @param config
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
     * @param config
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseExtension(XmlPullParser, boolean)
     */
    private Extension readTomlExtension(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "extension");

        var ext = new Extension();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "groupId":
                ext.setGroupId(config.getString(key));
                break;
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
     * @param config
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
     * @param config
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
     * @param config
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
     * @param config
     * @throws ModelParseException
     * @see MavenXpp3Reader#parsePlugin(XmlPullParser, boolean)
     */
    private List<Plugin> readTomlPlugins(TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config, "plugins");
        var ret = new ArrayList<Plugin>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlPlugins(config.getTable(i)));
        }
        return ret;
    }


    /**
     * @param config
     * @throws ModelParseException
     * @see MavenXpp3Reader#parsePlugin(XmlPullParser, boolean)
     */
    private Plugin readTomlPlugins(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config, "plugin");

        var plugin = new Plugin();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "groupId":
                plugin.setGroupId(config.getString(key));
                break;
            case "artifactId":
                plugin.setArtifactId(config.getString(key));
                break;
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
                plugin.setDependencies(readTomlDependencies(config.getArray(key)));
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
        }

        return plugin;
    }

    /**
     * @param config
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
     * @param config
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

    private void checkAttribute(String key, String attr) throws ModelParseException {
        if (isStrict) {
            throw new ModelParseException("Unrecognised attribute: '" + key + "." + attr + "'", -1, -1);
        }
    }

    private void checkTag(String tag) throws ModelParseException {
        if (isStrict) {
            throw new ModelParseException("Unrecognised tag: '" + tag + "'", -1, -1);
        }
    }

    private void checkTag(String key, String tag) throws ModelParseException {
        if (isStrict) {
            throw new ModelParseException("Unrecognised tag: '" + key + "." + tag + "'", -1, -1);
        }
    }
}
