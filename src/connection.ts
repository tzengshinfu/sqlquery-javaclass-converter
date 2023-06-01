export class Connection {
  constructor(
    readonly connectionName: string,
    readonly connectionDesc: string,
    readonly jdbcUrl: string,
    readonly userId: string,
    readonly password: string,
    readonly useSavedPassword: boolean
  ) {}
}
