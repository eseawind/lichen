// Copyright 2013 the original author or authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package lichen.migration.internal;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.sql.DataSource;

import lichen.migration.services.Migration;
import lichen.migration.services.MigrationHelper;
import lichen.migration.services.Options;

import org.apache.tapestry5.internal.plastic.PlasticClassPool;
import org.apache.tapestry5.plastic.ClassInstantiator;
import org.apache.tapestry5.plastic.ClassType;
import org.apache.tapestry5.plastic.PlasticClass;
import org.apache.tapestry5.plastic.PlasticClassEvent;
import org.apache.tapestry5.plastic.PlasticClassListener;
import org.apache.tapestry5.plastic.PlasticField;
import org.apache.tapestry5.plastic.PlasticManagerDelegate;
import org.apache.tapestry5.plastic.TransformationOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class Migrator {
    /**
     * The name of the table that stores all the installed migration
     * version numbers.
     */
    static final String SCHEMA_MIGRATIONS_TABLENAME = "schema_migrations";
    static final Logger LOGGER = LoggerFactory.getLogger(Migrator.class);
    private static final Options OPTIONS = new OptionsImpl();
    static final Set<String> PACKAGES = new HashSet<String>();
    static final PlasticClassPool PLASTIC_CLASS_POOL = new PlasticClassPool(
            Thread.currentThread().getContextClassLoader(), new PlasticManagerDelegate() {
        @Override
        public <T> ClassInstantiator<T> configureInstantiator(String className,
                                                              ClassInstantiator<T> instantiator) {
            return instantiator;
        }

        @Override
        public void transform(PlasticClass plasticClass) {
            List<PlasticField> fields = plasticClass.getFieldsWithAnnotation(Inject.class);
            for (PlasticField field : fields) {
                if (field.getTypeName().equals(MigrationHelper.class.getName())
                        || field.getTypeName().equals(Options.class.getName())) {
                    field.injectFromInstanceContext();
                } else {
                    throw new RuntimeException("wrong inject " + field.getName());
                }
            }
        }
    }, PACKAGES, new HashSet<TransformationOption>());

    {
        PACKAGES.add("lichen.migration.internal");
        //plasticManager.addPlasticClassListener(new PlasticClassListenerLogger(logger));
    }

    /**
     * Given a path to a JAR file, return a set of all the names of all
     * the classes the JAR file contains.
     *
     * @param path              path to the JAR file
     * @param packageName       the package name that the classes should be in
     * @param searchSubPackages true if sub-packages of packageName
     *                          should be searched
     * @return a set of the class names the JAR file contains
     */
    private static HashSet<String> classNamesInJar(String path,
                                                   String packageName,
                                                   final boolean searchSubPackages) throws IOException {
        // Search for the package in the JAR file by mapping the package
        // name to the expected name in the JAR file, then append a '/' to
        // the package name to ensure that no matches are done on
        // different packages that share a common substring.
        final String pn = packageName.replace('.', '/') + '/';

        final HashSet<String> classNames = new HashSet<String>();
        ResourceUtils.jarFile(new JarFile(path, false), new Function1<JarFile, Object>() {
            public Object apply(JarFile jar) throws Throwable {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.startsWith(pn) && name.endsWith(".class")) {
                        String className = name.substring(0, name.length() - ".class".length())
                                .replace('/', '.');
                        if (searchSubPackages) {
                            classNames.add(className);
                        } else if (!className.substring(pn.length()).contains(".")) {
                            classNames.add(className);
                        }
                    }
                }
                return null;  //To change body of implemented methods use File | Settings | File Templates.
            }
        });
        return classNames;
    }

    /**
     * Given a java.io.File for a directory containing compiled Java and
     * Scala classes, return a set of all names of the classes the
     * directory contains.
     *
     * @param file              the java.io.File corresponding to the directory
     * @param packageName       the package name that the classes should be
     *                          in
     * @param searchSubPackages true if sub-packages of packageName
     *                          should be searched
     * @return a set of the class names the directory contains
     */
    private static HashSet<String> classNamesInDir(File file,
                                                   String packageName,
                                                   boolean searchSubPackages) {
        HashSet<String> classNames = new HashSet<String>();


        scan(classNames, file, packageName, searchSubPackages);

        return classNames;
    }

    private static void scan(HashSet<String> classNames,
                             File f,
                             String pn,
                             boolean searchSubPackages) {
        File[] childFiles = f.listFiles();
        File[] files = childFiles;
        if (childFiles == null) {
            files = new File[0];
        }
        for (File childFile : files) {
            if (searchSubPackages && childFile.isDirectory()) {
                String childPackageName = childFile.getName();
                if (pn.length() > 0) {
                    childPackageName = pn + '.' + childFile.getName();
                }
                scan(classNames, childFile, childPackageName, searchSubPackages);
            } else if (childFile.isFile()) {
                String name = childFile.getName();
                if (name.endsWith(".class")) {
                    String className = pn + '.' + name.substring(0, name.length() - ".class".length());
                    classNames.add(className);
                }
            }
        }
    }

    /**
     * Given a resource's URL, return the names of all the classes in
     * the resource.
     *
     * @param url               the resource's URL
     * @param packageName       the Java package name to search for Migration
     *                          subclasses
     * @param searchSubPackages true if sub-packages of packageName
     *                          should be searched
     * @return a set of class names in the resource
     */
    private static HashSet<String> classNamesInResource(
            URL url,
            String packageName,
            boolean searchSubPackages) throws IOException {
        String u = URLDecoder.decode(url.toString(), "UTF-8");

        if (u.startsWith("jar:file:")) {
            // This URL ends with a ! character followed by the name of the
            // resource in the jar file, so just get the jar file path.
            int index = u.lastIndexOf('!');
            String path;
            if (index == -1) {
                path = u.substring("jar:file:".length());
            } else {
                path = u.substring("jar:file:".length(), index);
            }
            return classNamesInJar(path, packageName, searchSubPackages);
        } else if (u.startsWith("file:")) {
            String dir = u.substring("file:".length());
            File file = new File(dir);
            if (!file.isDirectory()) {
                String message = "The resource URL '" + u + "' should be a directory but is not.";
                throw new RuntimeException(message);
            }
            return classNamesInDir(file, packageName, searchSubPackages);
        } else {
            String message = "Do not know how to get a list of classes in the "
                    + "resource at '" + u + "' corresponding to the package '"
                    + packageName + "'.";
            throw new RuntimeException(message);
        }
    }

    /**
     * Given a Java package name, return a set of concrete classes with
     * a no argument constructor that implement Migration.
     * <p/>
     * Limitations:
     * 1) This function assumes that only a single directory or jar file
     * provides classes in the Java package.
     * 2) It will descend into non-child directories of the package
     * directory or other jars to find other migrations.
     * 3) It does not support remotely loaded classes and jar files.
     *
     * @param packageName       the Java package name to search for Migration
     *                          subclasses
     * @param searchSubPackages true if sub-packages of packageName
     *                          should be searched
     * @return a sorted map with version number keys and the concrete
     *         Migration subclasses as the value
     */
    private static SortedMap<Long, Class<? extends Migration>> findMigrations(
            String packageName,
            boolean searchSubPackages) throws IOException {
        // Ask the current class loader for the resources corresponding to
        // the package, which can refer to directories, jar files
        // accessible via the local filesystem or remotely accessible jar
        // files.  Only the first two are handled.
        String pn = packageName.replace('.', '/');

        Enumeration<URL> urls = Migrator.class.getClassLoader().getResources(pn);
        if (!urls.hasMoreElements()) {
            throw new RuntimeException("Cannot find a resource for package '" + packageName + "'.");
        }

        HashSet<String> classNames = new HashSet<String>();
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            //logger.debug("For package '{}' found resource at '{}'.",packageName, url);

            classNames.addAll(classNamesInResource(url,
                    packageName,
                    searchSubPackages));
        }

        // Search through the class names for ones that are concrete
        // subclasses of Migration that have a no argument constructor.
        // Use a sorted map mapping the version to the class name so the
        // final results will be sorted in numerically increasing order.
        TreeMap<Long, String> seenVersions = new TreeMap<Long, String>();
        HashMap<String, String> seenDescriptions = new HashMap<String, String>();

        // Search for classes that have the proper format.
        String reStr = "Migrate_(\\d+)_([_a-zA-Z0-9]*)";
        Pattern re = Pattern.compile(reStr);

        // Classes to be skipped.  classNames cannot have items removed from it
        // inside the for loop below or not all elements in classNames may be
        // visited (iterator corruption).
        HashSet<String> skipNames = new HashSet<String>();
        for (String className : classNames) {
            int index = className.lastIndexOf('.');
            String baseName;
            if (index == -1) {
                baseName = className;
            } else {
                baseName = className.substring(index + 1);
            }
            Matcher matcher = re.matcher(baseName);
            if (matcher.matches()) {
                String versionStr = matcher.group(1);
                Option<Long> versionOpt = Option.none();
                try {
                    versionOpt = Option.some(java.lang.Long.parseLong(versionStr));
                } catch (NumberFormatException e) {
                    skipNames.add(className);
                    LOGGER.debug("Skipping '{}' because the version string '{}' could not "
                            + "be parsed as a long integer.", className, versionStr);
                }

                if (versionOpt.isDefined()) {
                    Long version = versionOpt.get();
                    if (seenVersions.get(version) != null) {
                        String message = "The '"
                                + className
                                + "' migration defines a duplicate version number "
                                + "with '" + seenVersions.get(version) + "'.";
                        throw new IllegalArgumentException(message);
                    } else {
                        seenVersions.put(version, className);
                    }

                    String description = matcher.group(2);
                    if (seenDescriptions.get(description) != null) {
                        String message = "The '" + className
                                + "' defines a duplicate description with '"
                                + seenDescriptions.get(description) + "'.";
                        throw new IllegalArgumentException(message);
                    } else {
                        seenDescriptions.put(description, className);
                    }
                }
            } else {
                skipNames.add(className);
                //logger.debug("Skipping '{}' because it does not match '{}'.",className, reStr);
            }
        }

        // Remove all the skipped class names from classNames.
        classNames.removeAll(skipNames);

        TreeMap<Long, Class<? extends Migration>> results = new TreeMap<Long, Class<? extends Migration>>();
        Set<Map.Entry<Long, String>> entrySet = seenVersions.entrySet();
        for (Map.Entry<Long, String> entry : entrySet) {
            Long version = entry.getKey();
            String className = entry.getValue();
            Class<?> c = null;
            try {
                c = Class.forName(className);
                if (Migration.class.isAssignableFrom(c)
                        && !c.isInterface()
                        && !java.lang.reflect.Modifier.isAbstract(c.getModifiers())) {
                    try {
                        // Ensure that there is a no-argument constructor.
                        c.getConstructor();
                        Class<? extends Migration> castedClass = c.asSubclass(Migration.class);
                        results.put(version, castedClass);
                    } catch (NoSuchMethodException e) {
                        LOGGER.debug("Unable to find a no-argument constructor for '" + className + "'", e);
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Unable to load class '" + className + "'.", e);
            }
        }

        return results;
    }

    private ConnectionBuilder _connectionBuilder;
    private DatabaseAdapter _adapter;

    /**
     * This class migrates the database into the desired state.
     */
    public Migrator(ConnectionBuilder newConnectionBuilder, DatabaseAdapter newAdapter) {
        this._connectionBuilder = newConnectionBuilder;
        this._adapter = newAdapter;
    }

    public Migrator(DataSource dataSource, DatabaseAdapter databaseAdapter) {
        this(new ConnectionBuilder(dataSource), databaseAdapter);
    }

    /**
     * Construct a migrator to a database that needs a username and password.
     *
     * @param jdbcUrl      the JDBC URL to connect to the database
     * @param jdbcUsername the username to log into the database
     * @param jdbcPassword the password associated with the database
     *                     username
     * @param newAdapter   a concrete DatabaseAdapter that the migrator uses
     *                     to handle database specific features
     */
    public Migrator(String jdbcUrl,
                    String jdbcUsername,
                    String jdbcPassword,
                    DatabaseAdapter newAdapter) {
        this(new ConnectionBuilder(jdbcUrl, jdbcUsername, jdbcPassword), newAdapter);
    }


    /**
     * Get a list of table names.  If the database adapter was given a
     * schema name then only the tables in that schema are returned.
     *
     * @return a set of table names; no modifications of the case of
     *         table names is done
     */
    public Set<String> getTableNames() {
        return _connectionBuilder.withConnection(ResourceUtils.CommitBehavior.AutoCommit,
                new Function1<Connection, Set<String>>() {
                    public Set<String> apply(Connection connection) throws Throwable {
                        String schemaPattern = null;
                        if (_adapter.getSchemaNameOpt().isDefined()) {
                            schemaPattern = _adapter.unquotedNameConverter(_adapter.getSchemaNameOpt().get());
                        }
                        DatabaseMetaData metadata = connection.getMetaData();
                        return ResourceUtils.autoClosingResultSet(metadata.getTables(null,
                                schemaPattern,
                                null,
                                new String[]{"TABLE"}), new Function1<ResultSet, Set<String>>() {
                            public Set<String> apply(ResultSet rs) throws Throwable {
                                Set<String> names = new HashSet<String>();
                                final int index = 3;
                                while (rs.next()) {
                                    names.add(rs.getString(index).trim());
                                }
                                return names;
                            }
                        });
                    }
                });
    }

    /**
     * Execute a migration in the given direction.
     *
     * @param migrationClass   the class of migration to execute
     * @param direction        the direction the migration should be run
     * @param versionUpdateOpt if provided, the schema_migrations table
     *                         is updated using the given connection and migration
     *                         version number; this allows this method to
     */
    private void runMigration(Connection connection, Class<? extends Migration> migrationClass,
                              final MigrationDirection direction,
                              Option<Long> versionUpdateOpt,final String packageName) throws Throwable {
        //logger.info("Migrating {} with '{}'.",direction, migrationClass.getName());
        final int size = 80;
        System.out.println(horizontalLine("Applying: " + migrationClass.getSimpleName(), size));

        ClassInstantiator<? extends Migration> classInstantiator
                = PLASTIC_CLASS_POOL.getClassInstantiator(migrationClass.getName());
        final MigrationHelperImpl helper = new MigrationHelperImpl();
        //执行脚本的试试设置当前数据库厂商。
        helper.setAdapterOpt(Option.some(_adapter));
        helper.setRawConnectionOpt(Option.some(connection));
        helper.setConnectionOpt(Option.some(connection));

        Migration migration = classInstantiator.with(Options.class,
                OPTIONS).with(MigrationHelper.class, helper).newInstance();
        //根据操作类型，执行对应脚本类文件里的相应操作。
        switch (direction) {
            case Up:
                migration.up();
                break;
            case Down:
                migration.down();
                break;
            default:
                break;
        }
        //更新schema_migrations表里的脚本版本号
        if (versionUpdateOpt.isDefined() && null != packageName) {
            final Long version = versionUpdateOpt.get();
            String tableName = _adapter.quoteTableName(SCHEMA_MIGRATIONS_TABLENAME);
            String sql = "";
            switch (direction) {
                case Up:
                    sql = "INSERT INTO " + tableName + " (" + _adapter.quoteColumnName("module_name") +","+ _adapter.quoteColumnName("version") + ") VALUES (?,?)";
                    break;
                case Down:
                    sql = "DELETE FROM " + tableName + " WHERE " + _adapter.quoteColumnName("module_name") +"=? AND "+ _adapter.quoteColumnName("version") + "= ?";
                    break;
                default:
                    break;
            }
            System.out.println("更新版本"+version+"="+sql);
            ResourceUtils.autoClosingStatement(connection.prepareStatement(sql),
                    new Function1<PreparedStatement, Object>() {
                        public Object apply(PreparedStatement statement) throws Throwable {
                            statement.setString(1, packageName);
                            statement.setString(2, version.toString());
                            statement.execute();
                            return null;
                        }
                    });
        }
        //System.out.println(horizontalLine("OK", 80));
    }

    /**
     * Determine if the "schema_migrations" table exists.
     *
     * @return true if the "schema_migration" table exists
     */
    private boolean doesSchemaMigrationsTableExist() {
        String smtn = Migrator.SCHEMA_MIGRATIONS_TABLENAME.toLowerCase();
        for (String name : getTableNames()) {
            if (name.equalsIgnoreCase(smtn)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates the schema migrations table if it does not exist.
     */
    private void initializeSchemaMigrationsTable() throws Throwable {
        if (!doesSchemaMigrationsTableExist()) {
            final Option<Long> version = Option.none();
            _connectionBuilder.withConnection(ResourceUtils.CommitBehavior.AutoCommit,
                    new Function1<Connection, Object>() {
                        public Object apply(Connection parameter) throws Throwable {
                            runMigration(parameter, CreateSchemaMigrationsTableMigration.class,
                                    MigrationDirection.Up, version,null);
                            return null;
                        }
                    });
        }
    }

    /**
     * Get a sorted list of all the installed migrations using a query
     * on the given connection.
     *
     * @return a sorted set of version numbers of the installed
     *         migrations
     */
    private SortedSet<Long> getInstalledVersions(final String packageName) throws Throwable {
        return _connectionBuilder.withConnection(
                ResourceUtils.CommitBehavior.AutoCommit,
                new Function1<Connection, SortedSet<Long>>() {
                    public SortedSet<Long> apply(Connection parameter)
                            throws Throwable {
                        return getInstalledVersions(parameter,packageName);
                    }
                });
    }

    private SortedSet<Long> getInstalledVersions(Connection connection,String packageName) throws Throwable {
        final String sql = "SELECT " + _adapter.quoteColumnName("version")
                + " FROM " + _adapter.quoteTableName(SCHEMA_MIGRATIONS_TABLENAME)+"WHERE MODULE_NAME = '"+packageName+"'";
        System.out.println("查询已安装的版本="+sql);
        return ResourceUtils.autoClosingStatement(connection
                .prepareStatement(sql),
                new Function1<PreparedStatement, SortedSet<Long>>() {
                    public SortedSet<Long> apply(PreparedStatement parameter)
                            throws Throwable {
                        return ResourceUtils.autoClosingResultSet(parameter
                                .executeQuery(),
                                new Function1<ResultSet, SortedSet<Long>>() {
                                    public SortedSet<Long> apply(ResultSet rs)
                                            throws Throwable {
                                        TreeSet<Long> versions = new TreeSet<Long>();
                                        while (rs.next()) {
                                            String versionStr = rs.getString(1);
                                            try {
                                                Long version = java.lang.Long
                                                        .parseLong(versionStr);
                                                versions.add(version);
                                            } catch (NumberFormatException e) {
                                                LOGGER.warn("Ignoring installed migration with unparsable "
                                                        + "version number '" + versionStr
                                                        + "'.", e);
                                            }
                                        }
                                        return versions;
                                    }
                                });
                    }
                });
    }


    /**
     * Migrate the database.
     * <p/>
     * Running this method, even if no concrete Migration subclasses are
     * found in the given package name, will result in the creation of
     * the schema_migrations table in the database, if it does not
     * currently exist.
     *
     * @param packageName       the Java package name to search for migration
     *                          subclasses
     * @param searchSubPackages true if sub-packages of packageName
     *                          should be searched
     * @param operation         the migration operation that should be performed
     */
    public void migrate(final MigratorOperation operation,
                        final String packageName,
                        final boolean searchSubPackages) throws Throwable {
        PACKAGES.add(packageName);
        //initializeSchemaMigrationsTable();
        // Get a new connection that locks the schema_migrations table.
        // This will prevent concurrent migrations from running.  Commit
        // any modifications to schema_migrations regardless if an
        // exception is thrown or not, this ensures that any migrations
        // that were successfully run are recorded.
        _connectionBuilder.withConnection(ResourceUtils.CommitBehavior.CommitUponReturnOrException,
                new Function1<Connection, Object>() {
                    public Object apply(Connection schemaConnection) throws Throwable {
                        initializeSchemaMigrationsTable();

                        //logger.debug("Getting an exclusive lock on the '{}' table.",SCHEMA_MIGRATIONS_TABLENAME);
                        ResourceUtils.autoClosingStatement(schemaConnection.prepareStatement(
                                _adapter.lockTableSql(SCHEMA_MIGRATIONS_TABLENAME)),
                                new Function1<PreparedStatement, Object>() {
                                    @Override
                                    public Object apply(PreparedStatement parameter) throws Throwable {
                                        return parameter.execute();
                                    }
                                });
                        // Get a list of all available and installed migrations.  Check
                        // that all installed migrations have a migration class
                        // available to migrate out of that migration.  This can happen
                        // if the migration is applied by one copy of an application but
                        // another copy does not have that migration, say the migration
                        // was not checked into a source control system.  Having a
                        // missing migration for an installed migration is not fatal
                        // unless the migration needs to be rolled back.
                        SortedSet<Long> installedVersions = getInstalledVersions(schemaConnection,packageName);
                        SortedMap<Long, Class<? extends Migration>> availableMigrations
                                = findMigrations(packageName, searchSubPackages);
                        Set<Long> availableVersions = availableMigrations.keySet();

                        for (Long installedVersion : installedVersions) {
                            if (!availableVersions.contains(installedVersion)) {
                                LOGGER.warn("The migration version '{}' is installed but "
                                        + "there is no migration class available to back " + "it out.",
                                        installedVersion);
                            }
                        }
                        if (availableMigrations.isEmpty()) {
                            LOGGER.info("No migrations found, nothing to do.");
                        }
                        Long[] installVersions = new Long[0];
                        Long[] removeVersions = new Long[0];

                        // From the operation, determine the migrations to install and
                        // the ones to uninstall.
                        switch (operation) {
                            case InstallAllMigrations:
                                installVersions = availableVersions.toArray(new Long[availableVersions.size()]);
                                removeVersions = new Long[]{};
                                break;
                            case RemoveAllMigrations:
                                installVersions = new Long[]{};

                                removeVersions = new Long[installedVersions.size()];
                                Iterator<Long> it = installedVersions.iterator();
                                int size = removeVersions.length;
                                int i = 0;
                                while (it.hasNext()) {
                                    i += 1;
                                    removeVersions[size - i] = it.next();
                                }
                                break;
                            case MigrateToVersion:
                                List<Long> prepareInstallVersions = new ArrayList<Long>();
                                List<Long> prepareRemoveVersions = new ArrayList<Long>();
                                Iterator<Long> aIt = availableVersions.iterator();
                                boolean versionFound = false;
                                while (aIt.hasNext()) {
                                    Long version = aIt.next();
                                    if (version <= operation.getVersion()) {
                                        prepareInstallVersions.add(version);
                                    } else {
                                        prepareRemoveVersions.add(0, version);
                                    }
                                    if (version == operation.getVersion()) {
                                        versionFound = true;
                                    }
                                }
                                if (!versionFound) {
                                    String message = "The target version " + operation.getVersion()
                                            + " does not exist as a migration.";
                                    throw new RuntimeException(message);
                                }
                                installVersions = prepareInstallVersions.toArray(
                                                new Long[prepareInstallVersions.size()]);
                                removeVersions = prepareRemoveVersions.toArray(new Long[prepareRemoveVersions.size()]);
                                break;
                            case RollbackMigration:
                                if (operation.getCount() > installedVersions.size()) {
                                    String message = "Attempting to rollback " + operation.getCount()
                                            + " migrations but the database only has " + installedVersions.size()
                                            + " installed in it.";
                                    throw new RuntimeException(message);
                                }
                                installVersions = new Long[]{};
                                removeVersions = new Long[operation.getCount()];
                                it = installedVersions.iterator();
                                i = 0;
                                while (it.hasNext()) {
                                    i++;
                                    removeVersions[operation.getCount() - i] = it.next();
                                }
                                break;
                            default:
                                break;
                        }

                        // Always remove migrations before installing new ones.
                        Class<? extends Migration> clazz;
                        for (Long removeVersion : removeVersions) {
                            // At the beginning of the method it wasn't a fatal error to
                            // have a missing migration class for an installed migration,
                            // but when it cannot be removed, it is.
                            clazz = availableMigrations.get(removeVersion);
                            if (clazz != null) {
                                runMigration(schemaConnection, clazz, MigrationDirection.Down,
                                        Option.some(removeVersion),packageName);
                            } else {
                                String message = "The database has migration version '" + removeVersion
                                        + "' installed but there is no migration class "
                                        + "available with that version.";
                                LOGGER.error(message);
                                throw new IllegalStateException(message);
                            }
                        }

                        for (Long installVersion : installVersions) {
                            if (!installedVersions.contains(installVersion)) {
                                clazz = availableMigrations.get(installVersion);
                                if (clazz != null) {
                                    runMigration(schemaConnection, clazz, MigrationDirection.Up,
                                            Option.some(installVersion),packageName);
                                } else {
                                    String message = "Illegal state: trying to install a migration "
                                            + "with version '" + installVersion + "' that should exist.";
                                    throw new IllegalStateException(message);
                                }
                            }
                        }
                        return null;
                    }
                });
    }

    protected String horizontalLine(String caption, int length) {
        StringBuilder builder = new StringBuilder();
        builder.append("==========");
        if (caption.length() > 0) {
            caption = " " + caption + " ";
            builder.append(caption);
        }
        final int size = 10;
        for (int i = 0; i < length - caption.length() - size; i++) {
            builder.append("=");
        }
        return builder.toString();
    }

    /**
     * Get the status of all the installed and available migrations.  A
     * tuple-like class is returned that contains three groups of
     * migrations: installed migrations with an associated Migration
     * subclass, installed migration without an associated Migration
     * subclass and Migration subclasses that are not installed.
     *
     * @param packageName       the Java package name to search for Migration
     *                          subclasses
     * @param searchSubPackages true if sub-packages of packageName
     *                          should be searched
     */
    public MigrationStatuses getMigrationStatuses(String packageName,
                                                  boolean searchSubPackages) throws Throwable {
        SortedMap<Long, Class<? extends Migration>> availableMigrations
                = findMigrations(packageName, searchSubPackages);
        SortedSet<Long> installedVersions;
        if (doesSchemaMigrationsTableExist()) {
            installedVersions = getInstalledVersions(packageName);
        } else {
            installedVersions = new TreeSet<Long>();
        }

        SortedMap<Long, Class<? extends Migration>> notInstalled = availableMigrations;
        SortedMap<Long, Class<? extends Migration>> installedWithAvailableImplementation
                = new TreeMap<Long, Class<? extends Migration>>();
        TreeSet<Long> installedWithoutAvailableImplementation = new TreeSet<Long>();

        for (Long installedVersion : installedVersions) {
            notInstalled.remove(installedVersion);
            Class<? extends Migration> clazz = availableMigrations.get(installedVersion);
            if (clazz != null) {
                installedWithAvailableImplementation.put(installedVersion, clazz);
            } else {
                installedWithoutAvailableImplementation.add(installedVersion);
            }
        }

        return new MigrationStatuses(notInstalled,
                installedWithAvailableImplementation,
                installedWithoutAvailableImplementation);
    }

    /**
     * Determine if the database has all available migrations installed
     * in it and no migrations installed that do not have a
     * corresponding concrete Migration subclass; that is, the database
     * must have only those migrations installed that are found by
     * searching the package name for concrete Migration subclasses.
     * <p/>
     * Running this method does not modify the database in any way.  The
     * schema migrations table is not created.
     *
     * @param packageName       the Java package name to search for Migration
     *                          subclasses
     * @param searchSubPackages true if sub-packages of packageName
     *                          should be searched
     * @return None if all available migrations are installed and all
     *         installed migrations have a corresponding Migration
     *         subclass; Some(message) with a message suitable for
     *         logging with the not-installed migrations and the
     *         installed migrations that do not have a matching
     *         Migration subclass
     */
    public Option<String> whyNotMigrated(String packageName, boolean searchSubPackages) throws Throwable {
        MigrationStatuses migrationStatuses = getMigrationStatuses(packageName, searchSubPackages);
        SortedMap<Long, Class<? extends Migration>> notInstalled = migrationStatuses.getNotInstalled();
        SortedSet<Long> installedWithoutAvailableImplementation
                = migrationStatuses.getInstalledWithoutAvailableImplementation();

        if (notInstalled.isEmpty() && installedWithoutAvailableImplementation.isEmpty()) {
            return Option.none();
        } else {
            final int size = 256;
            StringBuilder sb = new StringBuilder(size);
            sb.append("The database is not fully migrated because ");

            if (!notInstalled.isEmpty()) {
                sb.append("the following migrations are not installed: ");
                for (Class<? extends Migration> aClass : notInstalled.values()) {
                    sb.append(aClass.getName()).append(", ");
                }

                if (!installedWithoutAvailableImplementation.isEmpty()) {
                    sb.append(" and ");
                }
            }

            if (!installedWithoutAvailableImplementation.isEmpty()) {
                sb.append("the following migrations are installed without a " + "matching Migration subclass: ");
                for (Long anInstalledWithoutAvailableImplementation : installedWithoutAvailableImplementation) {
                    sb.append(anInstalledWithoutAvailableImplementation).append(",");
                }
            }

            sb.append('.');

            return Option.some(sb.toString());
        }
    }

    public class PlasticClassListenerLogger implements PlasticClassListener {
        private final Logger _logger;

        public PlasticClassListenerLogger(Logger newLogger) {
            this._logger = newLogger;
        }

        public void classWillLoad(PlasticClassEvent event) {
            if (_logger.isDebugEnabled()) {
                Marker marker = MarkerFactory.getMarker(event.getPrimaryClassName());

                String extendedClassName = "";
                if (event.getType() == ClassType.PRIMARY) {
                    extendedClassName = event.getPrimaryClassName();
                } else {
                    extendedClassName = String.format("%s (%s for %s)", event.getClassName(),
                            event.getType(), event.getPrimaryClassName());

                }
                _logger.debug(marker,
                        String.format("Loading class %s:\n%s", extendedClassName, event.getDissasembledBytecode()));
            }
        }
    }
}
