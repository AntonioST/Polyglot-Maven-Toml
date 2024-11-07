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
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.sonatype.maven.polyglot.io.ModelReaderSupport;

@SuppressWarnings("unused")
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
        readTomlProperties(model, toml.getTable("properties"));
        // TODO dependencyManagement
        readTomlDependencies(model, toml.getArray("dependency"));

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
            switch (key) {
            case "parent":
                model.setParent(readTomlParent(config.getTable(key)));
                break;
            case "model-version":
            case "modelVersion":
                model.setModelVersion(config.getString(key));
                break;
            case "group-id":
            case "groupId":
                model.setGroupId(config.getString(key));
                break;

            case "artifact-id":
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
            case "inception-year":
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
            case "mailing-list":
            case "mailing-lists":
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
            case "issue-management":
            case "issueManagement":
            case "ci-management":
            case "ciManagement":
            case "distribution-management":
            case "distributionManagement":
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
            switch (key) {
            case "group-id":
            case "groupId":
                parent.setGroupId(config.getString(key));
                break;
            case "artifact-id":
            case "artifactId":
                parent.setArtifactId(config.getString(key));
                break;
            case "version":
                parent.setVersion(config.getString(key));
                break;
            case "relative-path":
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
            switch (key) {
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
            switch (key) {
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
            switch (key) {
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
            case "organization-url":
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
            switch (key) {
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
            case "other-archive":
            case "other-archives":
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
            switch (key) {
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

        var ret = new ArrayList<Dependency>(config.size());
        for (int i = 0; i < config.size(); i++) {
            ret.add(readTomlDependency(config.getTable(i)));
        }

        model.setDependencies(ret);
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
            switch (key) {
            case "group-id":
            case "groupId":
                dep.setGroupId(config.getString(key));
                break;
            case "artifact-id":
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
            case "system-path":
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

    private List<String> asStringList(Object obj) {
        if (obj instanceof String s) {
            return List.of(s);
        } else if (obj instanceof TomlArray array) {
            return array.toList().stream().map(Object::toString).toList();
        } else {
            return null;
        }
    }
}
