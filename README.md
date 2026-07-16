## K_DB
### Sqlite clone in pure kotlin
#### Possible commands:
###### Data commands:
1. Insert `insert id username_val email_val` - insert data
2. Delete `delete id` - deletes row by id
3. Update `update id set values username=username_val email=email_val` - updates listed values by id
4. Select `select` prints all rows
###### Meta commands:
1. .q/.exit - exit and flush,the only way to store data on disk
2. .dump_page `.dump_page page number` - prints dump of provided page with nodes info
3. .dump_tree - prints the whole tree of database with page and nodes info
 * 61 tests