package com.tzengshinfu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.text.CaseUtils;
import org.springframework.jdbc.support.rowset.ResultSetWrappingSqlRowSetMetaData;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;
import org.sqlite.SQLiteDataSource;

import com.google.common.io.Resources;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.mysql.cj.jdbc.MysqlDataSource;

import oracle.jdbc.datasource.impl.OracleDataSource;

public final class App {
    private App() {
    }

    public static void main(String[] args) throws IOException, SQLException {
        try {
            System.out.println(getTemplateText(args[0], args[1], args[2], args[3], args[4], args[5], args[6]));
        } catch (Exception e) {
            System.err.println(getExceptionText(e));
        }
    }

    private static String getTemplateText(String templateType, String packageName, String className, String jdbcUrl,
            String userId, String password, String sqlText) throws IOException, SQLException {
        switch (templateType) {
            case "class": {
                String classTemplateText = Resources.toString(Resources.getResource("class.template"),
                        StandardCharsets.UTF_8);
                String methodTemplateText = Resources.toString(Resources.getResource("method.template"),
                        StandardCharsets.UTF_8);
                String constructorTemplateText = Resources.toString(Resources.getResource("constructor.template"),
                        StandardCharsets.UTF_8);
                List<PropertyTypeInfo> propertyTypeInfos = getPropertyTypeInfos(jdbcUrl, userId, password, sqlText);
                String importsText = getImportsText(propertyTypeInfos);
                String propertiesText = getPropertiesText(propertyTypeInfos);
                String methodParametersText = getMethodParametersText(propertyTypeInfos);
                String assignmentsText = getAssignmentsText(propertyTypeInfos);
                String constructorsText = "\n" + String.format(constructorTemplateText, className, className,
                        methodParametersText, assignmentsText);
                String methodsText = getMethodsText(propertyTypeInfos, methodTemplateText);
                String parametersText = getParametersText(propertyTypeInfos);
                String objectsEqualsesText = getObjectsEqualsesText(propertyTypeInfos);
                String toStringMethodText = getToStringMethodText(propertyTypeInfos, className);
                return String.format(classTemplateText, packageName, importsText, className, propertiesText,
                        constructorsText, methodsText, className, className, className, objectsEqualsesText,
                        parametersText, toStringMethodText);
            }
            case "lombok": {
                String lombokTemplateText = Resources.toString(Resources.getResource("lombok.template"),
                        StandardCharsets.UTF_8);
                List<PropertyTypeInfo> propertyTypeInfos = getPropertyTypeInfos(jdbcUrl, userId, password, sqlText);
                String importsText = getImportsText(propertyTypeInfos);
                String propertiesText = getPropertiesText(propertyTypeInfos);
                return String.format(lombokTemplateText, packageName, importsText, className, propertiesText);
            }
            case "record": {
                String recordTemplateText = Resources.toString(Resources.getResource("record.template"),
                        StandardCharsets.UTF_8);
                List<PropertyTypeInfo> propertyTypeInfos = getPropertyTypeInfos(jdbcUrl, userId, password, sqlText);
                String importsText = getImportsText(propertyTypeInfos);
                String methodParametersText = getMethodParametersText(propertyTypeInfos);
                return String.format(recordTemplateText, packageName, importsText, className, methodParametersText);
            }
            default: {
                throw new RuntimeException(String
                        .format("Incorrect template type \"%s\" (Only accepts class/lombok/record)", templateType));
            }
        }
    }

    private static String getExceptionText(Exception e) {
        if (e.getCause() != null) {
            return e.getCause().getMessage();
        } else if (e instanceof ArrayIndexOutOfBoundsException) {
            return String.format("Incorrect number of arguments (Expected: 7, Actual:%s)", Integer.toString(
                    Integer.parseInt(e.getMessage().toString().substring(e.getMessage().toString().length() - 1))));
        } else {
            return e.getMessage();
        }
    }

    private static List<PropertyTypeInfo> getPropertyTypeInfos(String jdbcUrl, String userId, String password,
            String sqlText)
            throws SQLException {
        // Convert named parameterized query into parameterized query
        String parameterizedSQLText = sqlText.replaceAll(
                ":(.+?)(~|`|!|@|#|%|\\^|&|\\*|\\(|\\)|-|\\+|=|\\|\\\\|}|]|\\{|\\[|:|;|\"|'|\\?|\\/|>|\\.|<|,| |$)",
                "?$2");
        DataSource dataSource = getDataSource(jdbcUrl, userId, password);
        PreparedStatement preparedStatement = dataSource.getConnection().prepareStatement(parameterizedSQLText);
        SqlRowSetMetaData metaData = new ResultSetWrappingSqlRowSetMetaData(preparedStatement.getMetaData());

        List<PropertyTypeInfo> propertyTypeInfos = new ArrayList<PropertyTypeInfo>();

        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String originalTypeName = getRealColumnClassName(jdbcUrl, metaData, i);
            String simpleTypeName = getSimpleTypeName(originalTypeName);
            String propertyName = getPropertyName(metaData.getColumnLabel(i));

            propertyTypeInfos.add(new PropertyTypeInfo(originalTypeName, simpleTypeName, propertyName));
        }

        return propertyTypeInfos;
    }

    private static String getImportsText(List<PropertyTypeInfo> propertyTypeInfos) {
        List<String> imports = new ArrayList<String>();

        for (int i = 0; i < propertyTypeInfos.size(); i++) {
            String originalTypeName = propertyTypeInfos.get(i).getOriginalTypeName();

            if (originalTypeName.contains("java.lang")) {
                continue;
            }

            if (originalTypeName.contains("byte[]")) {
                continue;
            }

            imports.add(getImport(originalTypeName));
        }

        return imports.size() > 0 ? "\n" + String.join("\n", imports) + "\n" : "";
    }

    private static String getPropertiesText(List<PropertyTypeInfo> propertyTypeInfos) {
        List<String> properties = new ArrayList<String>();

        for (int i = 0; i < propertyTypeInfos.size(); i++) {
            String simpleTypeName = propertyTypeInfos.get(i).getSimpleTypeName();
            String propertyName = propertyTypeInfos.get(i).getPropertyName();

            properties.add(getProperty(simpleTypeName, propertyName));
        }

        return properties.size() > 0 ? "    " + String.join("\n    ", properties) : "";
    }

    private static String getAssignmentsText(List<PropertyTypeInfo> propertyTypeInfos) {
        List<String> assignments = new ArrayList<String>();

        for (int i = 0; i < propertyTypeInfos.size(); i++) {
            String propertyName = propertyTypeInfos.get(i).getPropertyName();

            assignments.add(getAssignment(propertyName));
        }

        return assignments.size() > 0 ? "    " + String.join("\n        ", assignments) : "";
    }

    private static String getMethodParametersText(List<PropertyTypeInfo> propertyTypeInfos) {
        List<String> methodParameters = new ArrayList<String>();

        for (int i = 0; i < propertyTypeInfos.size(); i++) {
            String simpleTypeName = propertyTypeInfos.get(i).getSimpleTypeName();
            String propertyName = propertyTypeInfos.get(i).getPropertyName();

            methodParameters.add(getMethodParameter(simpleTypeName, propertyName));
        }

        return methodParameters.size() > 0 ? String.join(", ", methodParameters) : "";
    }

    private static String getMethodsText(List<PropertyTypeInfo> propertyTypeInfos, String methodTemplateText) {
        List<String> methods = new ArrayList<String>();

        for (int i = 0; i < propertyTypeInfos.size(); i++) {
            String simpleTypeName = propertyTypeInfos.get(i).getSimpleTypeName();
            String propertyName = propertyTypeInfos.get(i).getPropertyName();

            methods.add(getMethod(methodTemplateText, simpleTypeName, propertyName));
        }

        return methods.size() > 0 ? String.join("\n", methods) : "";
    }

    private static String getParametersText(List<PropertyTypeInfo> propertyTypeInfos) {
        List<String> parameters = new ArrayList<String>();

        for (int i = 0; i < propertyTypeInfos.size(); i++) {
            String propertyName = propertyTypeInfos.get(i).getPropertyName();

            parameters.add(propertyName);
        }

        return parameters.size() > 0 ? String.join(", ", parameters) : "";
    }

    private static String getObjectsEqualsesText(List<PropertyTypeInfo> propertyTypeInfos) {
        List<String> objectsEqualses = new ArrayList<String>();

        for (int i = 0; i < propertyTypeInfos.size(); i++) {
            String propertyName = propertyTypeInfos.get(i).getPropertyName();

            objectsEqualses.add(getObjectsEquals(propertyName));
        }

        return objectsEqualses.size() > 0 ? String.join(" && ", objectsEqualses) : "";
    }

    private static String getToStringMethodText(List<PropertyTypeInfo> propertyTypeInfos, String className) {
        List<String> parameterValues = new ArrayList<String>();

        for (int i = 0; i < propertyTypeInfos.size(); i++) {
            String propertyName = propertyTypeInfos.get(i).getPropertyName();

            parameterValues.add(propertyName + "=\" + " + propertyName);
        }

        return parameterValues.size() > 0
                ? "\"" + className + "[" + String.join(" + \", \" + \"", parameterValues) + " + \"]" + "\""
                : "";
    }

    private static DataSource getDataSource(String jdbcUrl, String userId, String password) throws SQLException {
        DataSource dataSource = null;

        if (jdbcUrl.contains("jdbc:mysql")) {
            dataSource = new MysqlDataSource();
            ((MysqlDataSource) dataSource).setUrl(jdbcUrl);
            ((MysqlDataSource) dataSource).setUser(userId);
            ((MysqlDataSource) dataSource).setPassword(password);
        } else if (jdbcUrl.contains("jdbc:sqlserver")) {
            dataSource = new SQLServerDataSource();
            ((SQLServerDataSource) dataSource).setURL(jdbcUrl);
            ((SQLServerDataSource) dataSource).setUser(userId);
            ((SQLServerDataSource) dataSource).setPassword(password);
        } else if (jdbcUrl.contains("jdbc:oracle")) {
            dataSource = new OracleDataSource();
            ((OracleDataSource) dataSource).setURL(jdbcUrl);
            ((OracleDataSource) dataSource).setUser(userId);
            ((OracleDataSource) dataSource).setPassword(password);
        } else if (jdbcUrl.contains("jdbc:sqlite")) {
            dataSource = new SQLiteDataSource();
            ((SQLiteDataSource) dataSource).setUrl(jdbcUrl);
        } else {
            throw new RuntimeException(String.format(
                    "Unsupported JDBC type \"%s\" (Only accepts jdbc:mysql/jdbc:sqlserver/jdbc:oracle/jdbc:sqlite)",
                    jdbcUrl));
        }

        return dataSource;
    }

    private static String getImport(String typeName) {
        return String.format("import %s;", typeName);
    }

    private static String getPropertyName(String columnName) {
        return CaseUtils.toCamelCase(columnName.replaceAll("([a-z])()([A-Z])", "$1_$3"), false,
                new char[] { '.', '_', ' ', '$', '@', '#' });
    }

    private static String getSimpleTypeName(String fullTypeName) {
        if (fullTypeName.contains("java.lang.String")) {
            return "String";
        }

        if (fullTypeName.contains("java.lang.Integer")) {
            return "int";
        }

        if (fullTypeName.contains("java.lang")) {
            return getLowercaseFirstLetterText(fullTypeName.split("\\.")[fullTypeName.split("\\.").length - 1]);
        }

        if (fullTypeName == "byte[]") {
            return fullTypeName;
        }

        return fullTypeName.split("\\.")[fullTypeName.split("\\.").length - 1];
    }

    private static String getProperty(String simpleTypeName, String propertyName) {
        return String.format("private %s %s;", simpleTypeName, propertyName);
    }

    private static String getMethodParameter(String simpleTypeName, String propertyName) {
        return String.format("%s %s", simpleTypeName, propertyName);
    }

    private static String getAssignment(String propertyName) {
        return String.format("this.%s = %s;", propertyName, propertyName);
    }

    private static String getObjectsEquals(String propertyName) {
        return String.format("Objects.equals(this.%s, o1.%s)", propertyName, propertyName);
    }

    private static String getMethod(String methodTemplateText, String typeName, String propertyName) {
        return String.format(methodTemplateText, typeName, getCapitalizedFirstLetterText(propertyName), propertyName,
                getCapitalizedFirstLetterText(propertyName), typeName, propertyName, getAssignment(propertyName));
    }

    private static String getCapitalizedFirstLetterText(String text) {
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    private static String getLowercaseFirstLetterText(String text) {
        return text.substring(0, 1).toLowerCase() + text.substring(1);
    }

    private static String getRealColumnClassName(String jdbcUrl, SqlRowSetMetaData metaData, int columnIndex) {
        // #region SQLite
        if (jdbcUrl.contains("jdbc:sqlite")) {
            switch (metaData.getColumnTypeName(columnIndex)) {
                case "INTEGER":
                    return "java.lang.Integer";
                case "TEXT":
                    return "java.lang.String";
                case "BLOB":
                    return "java.sql.Blob";
                case "REAL":
                    return "java.lang.Float";
                case "NUMERIC":
                    return "java.math.BigDecimal";
            }
        }
        // #endregion

        // #region Equals "byte[]"
        if (metaData.getColumnClassName(columnIndex).equals("[B")) {
            return "byte[]";
        }
        // #endregion

        return metaData.getColumnClassName(columnIndex);
    }
}
