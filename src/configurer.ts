import * as vscode from "vscode";
import { Connection } from "./connection";

export class Configurer {
  public static connections(): Connection[] {
    const connections = Configurer.profile().get<Connection[]>("connections");

    if (connections) {
      return connections.filter(
        (conn) =>
          conn.connectionName !== "" &&
          conn.jdbcUrl !== "" &&
          conn.userId !== ""
      );
    }

    return [];
  }

  public static defaultTemplateType(): string | undefined {
    return Configurer.profile().get("defaultTemplateType");
  }

  public static useLastTemplateType(): boolean | undefined {
    return Configurer.profile().get("useLastTemplateType");
  }

  public static async saveLastTemplateType(templateType: string) {
    if (Configurer.cantSaveToWorkspace()) {
      vscode.window.showWarningMessage(
        "Attempted to save Template type to current Workspace settings, but failed due to no Workspace being opened"
      );
      return;
    }

    templateType =
      templateType.charAt(0).toUpperCase() + templateType.substring(1);

    await Configurer.profile().update(
      "defaultTemplateType",
      templateType,
      Configurer.target()
    );
  }

  public static defaultPackageName(): string | undefined {
    return Configurer.profile().get("defaultPackageName");
  }

  public static useLastPackageName(): boolean | undefined {
    return Configurer.profile().get("useLastPackageName");
  }

  public static async saveLastPackageName(packageName: string) {
    if (Configurer.cantSaveToWorkspace()) {
      vscode.window.showWarningMessage(
        "Attempted to save Package name to current Workspace settings, but failed due to no Workspace being opened"
      );
      return;
    }

    await Configurer.profile().update(
      "defaultPackageName",
      packageName,
      Configurer.target()
    );
  }

  public static defaultClassName(): string | undefined {
    return Configurer.profile().get("defaultClassName");
  }

  public static useLastClassName(): boolean | undefined {
    return Configurer.profile().get("useLastClassName");
  }

  public static async saveLastClassName(className: string) {
    if (Configurer.cantSaveToWorkspace()) {
      vscode.window.showWarningMessage(
        "Attempted to save Class name to current Workspace settings, but failed due to no Workspace being opened"
      );
      return;
    }

    await Configurer.profile().update(
      "defaultClassName",
      className,
      Configurer.target()
    );
  }

  private static profile(): vscode.WorkspaceConfiguration {
    return vscode.workspace.getConfiguration("sqlquery-javaclass-converter");
  }

  private static target(): vscode.ConfigurationTarget {
    const settingTarget = Configurer.profile().get("settingTarget");
    return settingTarget === "Workspace"
      ? vscode.ConfigurationTarget.Workspace
      : vscode.ConfigurationTarget.Global;
  }

  private static cantSaveToWorkspace(): boolean {
    return (
      !vscode.workspace.workspaceFolders &&
      Configurer.target() === vscode.ConfigurationTarget.Workspace
    );
  }
}
