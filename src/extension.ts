import * as vscode from "vscode";
import { Converter } from "./converter";

export function activate(context: vscode.ExtensionContext) {
  context.subscriptions.push(
    vscode.commands.registerCommand(
      "sqlquery-javaclass-converter.convert",
      async () => {
        await Converter.convert();
      }
    )
  );
}

export function deactivate() {}
