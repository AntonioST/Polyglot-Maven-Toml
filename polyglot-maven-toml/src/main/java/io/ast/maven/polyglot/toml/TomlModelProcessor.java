package io.ast.maven.polyglot.toml;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    private Model readToml(Reader input) throws IOException, ModelParseException {
        var toml = Toml.parse(input);

        var model = new Model();

        readTomlProject(model, toml.getTable("project"));
        if (toml.contains("parent")) {
            model.setParent(readTomlParent(toml.getTable("parent")));
        }

        readTomlProperties(model, toml.getTable("properties"));
        // TODO dependencyManagement
        readTomlDependencies(model, toml.getArray("dependency"));
        readTomlRepositories(model, toml.getArray("repositories"));
        // TODO pluginRepositories
        readTomlBuild(model, toml.getTable("build"));


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
                model.setMailingLists(readTomlMailingList(key, config.getArray(key)));
                break;
            case "prerequisites":
                model.setPrerequisites(readTomlPrerequisites(config.getTable(key)));
                break;
            case "module":
            case "modules":
                model.setModules(config.getArray(key).toList().stream().map(Object::toString).toList());
                break;
            case "scm":
            case "issueManagement":
            case "ciManagement":
            case "distributionManagement":
                // TODO
                System.out.println("Unsupported tag now: 'project." + key + "'");
                break;

            default:
                if (isStrict) {
                    throw new ModelParseException("Unrecognised tag: 'project." + key + "'", -1, -1);
                }
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
                if (isStrict) {
                    throw new ModelParseException("Unrecognised tag: 'parent." + key + "'", -1, -1);
                }
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
                if (isStrict) {
                    throw new ModelParseException("Unrecognised tag: 'project.organization." + key + "'", -1, -1);
                }
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
        Objects.requireNonNull(config);
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
        Objects.requireNonNull(config);

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
                if (isStrict) {
                    var name = license.getName();
                    if (name == null) {
                        throw new ModelParseException("Unrecognised tag: 'project.license." + key + "'", -1, -1);
                    } else {
                        throw new ModelParseException("Unrecognised tag: 'project.license[" + name + "]." + key + "'", -1, -1);
                    }
                }
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
        Objects.requireNonNull(config);
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
        Objects.requireNonNull(config);
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
                var roles = asStringList(config.get(key));
                if (roles == null) {
                    var name = contributor.getName();
                    throw new ModelParseException("Unrecognised value: 'project.developer[" + name + "]." + key + "'", -1, -1);
                }
                contributor.setRoles(roles);
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
                if (isStrict) {
                    var role = (contributor instanceof Developer) ? "developer" : "contributor";

                    var name = contributor.getName();
                    if (name == null) {
                        throw new ModelParseException("Unrecognised tag: 'project." + role + "." + key + "'", -1, -1);
                    } else {
                        throw new ModelParseException("Unrecognised tag: 'project." + role + "[" + name + "]." + key + "'", -1, -1);
                    }
                }
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
    private List<MailingList> readTomlMailingList(String p, TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config);
        var ret = new ArrayList<MailingList>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlMailingList(p, config.getTable(i)));
        }
        return ret;
    }

    /**
     * @param config
     * @return
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseMailingList(XmlPullParser, boolean)
     */
    private MailingList readTomlMailingList(String p, TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config);

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
            case "otherArchive":
            case "otherArchives":
                var archives = asStringList(config.get(key));
                if (archives == null) {
                    var name = mail.getName();
                    throw new ModelParseException("Unrecognised value: 'project.mailinglist[" + name + "]." + key + "'", -1, -1);
                }
                mail.setOtherArchives(archives);
                break;
            default:
                if (isStrict) {
                    var name = mail.getName();
                    if (name == null) {
                        throw new ModelParseException("Unrecognised tag: 'project." + p + "." + key + "'", -1, -1);
                    } else {
                        throw new ModelParseException("Unrecognised tag: 'project." + p + "[" + name + "]." + key + "'", -1, -1);
                    }
                }
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
        Objects.requireNonNull(config);

        var pre = new Prerequisites();
        for (var key : config.keySet()) {
            switch (toCamelCase(key)) {
            case "maven":
                pre.setMaven(config.getString(key));
                break;
            default:
                if (isStrict) {
                    throw new ModelParseException("Unrecognised tag: 'project.prerequisites." + key + "'", -1, -1);
                }
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
    private void readTomlProperties(Model model, TomlTable config) throws ModelParseException {
        if (config == null) return;
        for (var key : config.keySet()) {
            var value = config.get(key);
            if (value instanceof String v) {
                model.addProperty(key, v);
            } else if (value instanceof TomlTable table) {
                readTomlProperties(model, table, key);
            } else {
                if (isStrict) {
                    throw new ModelParseException("Unrecognised tag: 'properties[" + key + "]'", -1, -1);
                }
            }
        }
    }

    private void readTomlProperties(Model model, TomlTable config, String p) throws ModelParseException {
        for (var key : config.keySet()) {
            var value = config.get(key);
            if (value instanceof String v) {
                model.addProperty(p + "." + key, v);
            } else if (value instanceof TomlTable table) {
                readTomlProperties(model, table, p + "." + key);
            } else {
                if (isStrict) {
                    throw new ModelParseException("Unrecognised tag: 'properties[" + p + "." + key + "]'", -1, -1);
                }
            }
        }
    }

    /**
     * @param model
     * @param config
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseDependency(XmlPullParser, boolean)
     */
    private void readTomlDependencies(Model model, TomlArray config) throws ModelParseException {
        if (config == null) return;
        model.setDependencies(readTomlDependencies(config));
    }

    /**
     * @param model
     * @param config
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseDependency(XmlPullParser, boolean)
     */
    private List<Dependency> readTomlDependencies(TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config);

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
        Objects.requireNonNull(config);

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
                if (isStrict) {
                    var name = dep.getGroupId() + ":" + dep.getArtifactId();
                    throw new ModelParseException("Unrecognised tag: 'dependency[" + name + "]." + key + "'", -1, -1);
                }
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
    private void readTomlRepositories(Model model, TomlArray config) throws ModelParseException {
        if (config == null) return;

        var ret = new ArrayList<Repository>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlRepositories(config.getTable(i)));
        }

        model.setRepositories(ret);
    }

    /**
     * @param model
     * @param config
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseRepository(XmlPullParser, boolean)
     */
    private Repository readTomlRepositories(TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config);

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
                if (isStrict) {
                    var name = rep.getName();
                    throw new ModelParseException("Unrecognised tag: 'repository[" + name + "]." + key + "'", -1, -1);
                }
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
        Objects.requireNonNull(config);

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
                if (isStrict) {
                    throw new ModelParseException("Unrecognised tag: 'repository[" + repository + "]." + tag + "." + key + "'", -1, -1);
                }
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
    private void readTomlBuild(Model model, TomlTable config) throws ModelParseException {
        if (config == null) return;
        model.setBuild(readTomlBuild(config));
    }

    /**
     * @param model
     * @param config
     * @throws ModelParseException
     * @see MavenXpp3Reader#parseBuild(XmlPullParser, boolean)
     */
    private Build readTomlBuild(TomlTable config) throws ModelParseException {

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
                // TODO
                System.out.println("Unsupported tag now: 'build." + key + "'");
                break;
            default:
                if (isStrict) {
                    throw new ModelParseException("Unrecognised tag: 'build." + key + "'", -1, -1);
                }
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
        Objects.requireNonNull(config);
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
        Objects.requireNonNull(config);

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
                if (isStrict) {
                    var name = ext.getGroupId() + ":" + ext.getArtifactId();
                    throw new ModelParseException("Unrecognised tag: 'extension[" + name + "]." + key + "'", -1, -1);
                }
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
        Objects.requireNonNull(config);
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
        Objects.requireNonNull(config);

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
                if (isStrict) {
                    throw new ModelParseException("Unrecognised tag: 'resource." + key + "'", -1, -1);
                }
            }
        }

        return res;
    }

    /**
     * @param config
     * @throws ModelParseException
     * @see MavenXpp3Reader#parsePlugin(XmlPullParser, boolean)
     */
    private List<Plugin> readTomlPlugins(TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config);
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
        Objects.requireNonNull(config);

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
            case "executions": {
                var name = plugin.getGroupId() + ":" + plugin.getArtifactId();
                plugin.setExecutions(readTomlPluginExecutions(name, config.getArray(key)));
                break;
            }
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
                var dom = asDOM("configuration", config.getTable(key));
                System.out.println(dom);
                plugin.setConfiguration(dom);
                break;
            default:
                if (isStrict) {
                    var name = plugin.getGroupId() + ":" + plugin.getArtifactId();
                    throw new ModelParseException("Unrecognised tag: 'plugin[" + name + "]." + key + "'", -1, -1);
                }
            }
        }

        return plugin;
    }

    /**
     * @param config
     * @throws ModelParseException
     * @see MavenXpp3Reader#parsePluginExecution(XmlPullParser, boolean)
     */
    private List<PluginExecution> readTomlPluginExecutions(String plugin, TomlArray config) throws ModelParseException {
        Objects.requireNonNull(config);
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
    private PluginExecution readTomlPluginExecutions(String plugin, TomlTable config) throws ModelParseException {
        Objects.requireNonNull(config);

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
                if (isStrict) {
                    throw new ModelParseException("Unrecognised tag: 'plugin[" + plugin + "]." + key + "'", -1, -1);
                }
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
                throw new ModelParseException("Unrecognised tag: '" + key + "'", -1, -1);
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
}
