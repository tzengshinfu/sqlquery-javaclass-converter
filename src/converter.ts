import path from "path";
import * as vscode from "vscode";
import { Configurer } from "./configurer";
import { Connection } from "./connection";

export class Converter {
  public static async convert(): Promise<void> {
    try {
      //#region Input SQL query syntax
      const sqlQuerySyntax = await this.getSQLQuerySyntax();

      if (!sqlQuerySyntax) {
        vscode.window.showErrorMessage("No SQL query syntax inputed", {
          modal: true,
        });
        return;
      }
      //#endregion

      //#region Input/Select JDBC url/User id/Password
      let jdbcUrl: string | undefined;
      let userId: string | undefined;
      let password: string | undefined;
      let useSavedPassword: boolean | undefined;

      const selectedConnection = await this.getSelectedConnection(
        Configurer.connections()
      );

      if (!selectedConnection) {
        jdbcUrl = await this.getJDBCUrl();

        if (!jdbcUrl) {
          vscode.window.showErrorMessage("No JDBC url inputed", {
            modal: true,
          });
          return;
        }

        //SQLite needs no User id and Password, replace with '_'
        userId = jdbcUrl.startsWith("jdbc:sqlite")
          ? "_"
          : await this.getUserID();

        if (!userId) {
          vscode.window.showErrorMessage("No User id inputed", {
            modal: true,
          });
          return;
        }

        //SQLite needs no User id and Password, replace with '_'
        password = jdbcUrl.startsWith("jdbc:sqlite")
          ? "_"
          : await this.getPassword();

        //Allow blank password
        if (password === undefined) {
          vscode.window.showErrorMessage("No Password inputed", {
            modal: true,
          });
          return;
        }
      } else {
        jdbcUrl = selectedConnection.jdbcUrl;
        userId = selectedConnection.userId;
        useSavedPassword = selectedConnection.useSavedPassword;

        if (useSavedPassword) {
          password = selectedConnection.password;
        } else {
          //SQLite needs no User id and Password, replace with '_'
          password = jdbcUrl.startsWith("jdbc:sqlite")
            ? "_"
            : await this.getPassword();

          //Allow blank password
          if (password === undefined) {
            vscode.window.showErrorMessage("No Password inputed", {
              modal: true,
            });
            return;
          }
        }
      }
      //#endregion

      //#region Select Template type
      const templateType = await this.getTemplateType();

      if (!templateType) {
        vscode.window.showErrorMessage("No Template selected", {
          modal: true,
        });
        return;
      }
      //#endregion

      //#region Input Package name
      const packageName = await this.getPackageName();

      if (!packageName) {
        vscode.window.showErrorMessage("No Package name inputed", {
          modal: true,
        });
        return;
      }
      //#endregion

      //#region Input Class name
      const className = await this.getClassName();

      if (!className) {
        vscode.window.showErrorMessage("No Class name inputed", {
          modal: true,
        });
        return;
      }
      //#endregion

      //#region Open new Java class Document editor
      const { _stdout, _stderr } = await this.getJavaClass(
        templateType,
        packageName,
        className,
        jdbcUrl!,
        userId!,
        password!,
        sqlQuerySyntax
      );

      if (_stderr && !_stdout) {
        vscode.window.showErrorMessage(_stderr);
        return;
      }

      if (_stderr && _stdout) {
        vscode.window.showWarningMessage(_stderr);
      }

      await this.createNewDocument(className, _stdout!);
      //#endregion

      vscode.window.showInformationMessage("Conversion finished");

      //#region Save settings
      if (Configurer.useLastTemplateType()) {
        await Configurer.saveLastTemplateType(templateType);
      }

      if (Configurer.useLastPackageName()) {
        await Configurer.saveLastPackageName(packageName);
      }

      if (Configurer.useLastClassName()) {
        await Configurer.saveLastClassName(className);
      }
      //#endregion
    } catch (error: any) {
      if (error instanceof Error) {
        vscode.window.showErrorMessage(error.message, { modal: true });
      } else {
        vscode.window.showErrorMessage(error, { modal: true });
      }
    }
  }

  private static async getSQLQuerySyntax(): Promise<string | undefined> {
    let sqlQuery: string | undefined;

    const activeEditor = vscode.window.activeTextEditor;

    if (activeEditor) {
      const selectedSQLQuery = activeEditor.document.getText(
        activeEditor.selection
      );

      if (selectedSQLQuery) {
        sqlQuery = selectedSQLQuery;
      }
    }

    if (!sqlQuery) {
      const inputSQLQuery = await vscode.window.showInputBox({
        title: "Input SQL query syntax",
        placeHolder:
          "SQL query syntax (parameters can also be included) will be converted into Java data class",
        ignoreFocusOut: true,
      });

      if (inputSQLQuery) {
        sqlQuery = inputSQLQuery;
      }
    }

    return sqlQuery;
  }

  private static async getSelectedConnection(savedConnections: Connection[]) {
    if (savedConnections.length === 0) {
      return undefined;
    }

    const selectedConnection = await vscode.window.showQuickPick(
      savedConnections.map((conn) => {
        return {
          label: conn.connectionName,
          description: conn.connectionDesc,
        };
      }),
      {
        title: "Select a connection (press 'ESC' to manual input)",
        ignoreFocusOut: true,
      }
    );

    if (!selectedConnection) {
      return undefined;
    }

    const _selectedConnection = savedConnections.find((conn) => {
      return conn.connectionName === selectedConnection.label;
    })!;
    return _selectedConnection;
  }

  private static async getJDBCUrl(): Promise<string | undefined> {
    const jdbcUrl = await vscode.window.showInputBox({
      title: "Input JDBC url",
      placeHolder:
        "Only accept connection strings starting with jdbc:mysql/jdbc:sqlserver/jdbc:oracle/jdbc:sqlite",
      ignoreFocusOut: true,
    });
    return jdbcUrl;
  }

  private static async getUserID(): Promise<string | undefined> {
    const userId = await vscode.window.showInputBox({
      title: "Input User id",
      ignoreFocusOut: true,
    });
    return userId;
  }

  private static async getPassword(): Promise<string | undefined> {
    const password = await vscode.window.showInputBox({
      title: "Input Password",
      password: true,
      ignoreFocusOut: true,
    });
    return password;
  }

  private static async getTemplateType(): Promise<string | undefined> {
    const defaultTemplateType = Configurer.defaultTemplateType();
    const templateTypeItems = [
      {
        label: "Class",
        description: "",
      },
      {
        label: "Lombok",
        description: "Lombok plugin needs",
      },
      {
        label: "Record",
        description: "Java 14+ needs",
      },
    ];
    const firstItemIndex =
      defaultTemplateType === "Lombok"
        ? 1
        : defaultTemplateType === "Record"
        ? 2
        : 0;

    const firstTemplateTypeItems = templateTypeItems.splice(firstItemIndex);
    const sortedTemplateTypeItems =
      firstTemplateTypeItems.concat(templateTypeItems);
    const selectedTemplateType = await vscode.window.showQuickPick(
      sortedTemplateTypeItems,
      {
        title: "Select a template to create Java data class",
        ignoreFocusOut: true,
      }
    );

    if (!selectedTemplateType) {
      return undefined;
    }

    const templateType =
      selectedTemplateType.label.charAt(0).toLowerCase() +
      selectedTemplateType.label.substring(1);

    return templateType;
  }

  private static async getPackageName(): Promise<string | undefined> {
    const defaultPackageName = Configurer.defaultPackageName() ?? "";
    const packageName = await vscode.window.showInputBox({
      title: "Input Package name",
      value: defaultPackageName,
      ignoreFocusOut: true,
    });
    return packageName;
  }

  private static async getClassName(): Promise<string | undefined> {
    const defaultClassName = Configurer.defaultClassName() ?? "";
    const className = await vscode.window.showInputBox({
      title: "Input Class name",
      value: defaultClassName,
      ignoreFocusOut: true,
    });
    return className;
  }

  private static async getJavaClass(
    templateType: string,
    packageName: string,
    className: string,
    jdbcUrl: string,
    userId: string,
    password: string,
    sqlQuery: string
  ) {
    let _stdout, _stderr;
    const converter = vscode.Uri.file(
      path.join(
        vscode.extensions.getExtension(
          "tzengshinfu.sqlquery-javaclass-converter"
        )!.extensionPath,
        "resources",
        "sqlquery-javaclass-converter-component-0.0.1.jar"
      )
    );
    const util = require("util");
    await vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: "Converting...",
        cancellable: true,
      },
      async (progress, token) => {
        token.onCancellationRequested(() => {
          throw new Error("Conversion cancelled");
        });

        progress.report({ increment: 99 });

        const exec = util.promisify(require("child_process").exec);
        const { stdout, stderr } = await exec(
          `java -Dfile.encoding=UTF-8 -jar "${converter.fsPath}" "${templateType}" "${packageName}" "${className}" "${jdbcUrl}" "${userId}" "${password}" "${sqlQuery}"`
        );
        _stdout = stdout.toString();
        _stderr = stderr.toString();

        progress.report({ increment: 100 });
      }
    );
    return { _stdout, _stderr };
  }

  private static async createNewDocument(
    className: string,
    code: string
  ): Promise<void> {
    const newJavaClassDocumentUri = vscode.Uri.parse(
      `untitled:${className}.java`
    );
    const document = await vscode.workspace.openTextDocument(
      newJavaClassDocumentUri
    );
    const editor = await vscode.window.showTextDocument(
      document,
      vscode.ViewColumn.Beside,
      true
    );
    await editor.edit((edit) => {
      edit.insert(new vscode.Position(0, 0), code);
    });
  }
}
