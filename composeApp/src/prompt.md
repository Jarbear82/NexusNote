Implementation plan for reference:

### Phase 1: Database Schema Definition (`AppDatabase.sq`)

You will delete all existing tables and replace them with a normalized **Graph-Relational** schema.

**Step 1.1: Create Metadata Tables (The Type System)**
These tables define the rules of your world.

* **Schema Definitions:** Create a table to store the high-level types. It needs a column to distinguish between `ENTITY` (Nodes) and `RELATION` (Edges).
* **Attribute Definitions:** Create a table to define properties (e.g., "Age", "Date").
* It must link back to a Schema ID.
* It must have a "Data Type" column to enforce your strict typing (store string enums like "TEXT", "INTEGER").
* Enforce a Unique constraint on `(SchemaID, Name)` so a Schema can't have duplicate property names.


* **Role Definitions:** Create a table to define the roles a Relation can play (e.g., "Source", "Target", "Husband").
* It must link back to a Relation Schema ID.
* It needs columns for Direction (`Source`/`Target`) and Cardinality (`One`/`Many`).



**Step 1.2: Create Instance Tables (The Data)**
These tables store the actual graph.

* **Entity Identity:** Create a master `Entity` table. This is the primary key generator for *everything* (nodes and edges). It only needs an ID and a creation timestamp.
* **Entity Typing:** Create a join table (`EntityType`) to link an `EntityID` to a `SchemaID`.
* This supports **Composite Nodes**: You can insert multiple rows for the same Entity (e.g., Entity #1 is a "Person" AND a "Wizard").


* **Attribute Values:** Create a table to store property data.
* It links an `EntityID` and an `AttributeDefinitionID` to a value.
* **Strict Typing:** Instead of one text column, create separate nullable columns for each type: `val_text`, `val_int`, `val_bool`, `val_real`.
* This ensures "Age" is always stored in the integer column, allowing for mathematical sorting/filtering later.


* **Relation Linking:** Create a table to define the graph connections (`RelationLink`).
* It links three IDs: The `RelationEntityID` (the edge), the `PlayerEntityID` (the node/target), and the `RoleDefinitionID`.
* This replaces your old `EdgeLink` table but is now recursive: `PlayerEntityID` can point to another Relation.



---

### Phase 2: Domain Model Refactoring (Kotlin)

You need to update your data classes to reflect that "Nodes" and "Edges" now share a common ancestor.

**Step 2.1: Abstract the "Entity"**

* Create a base interface or sealed class (e.g., `CodexEntity`) that holds the ID and a list of `Schema` types.
* Update your `GraphNode` model. Instead of a single `label: String`, it should now hold `types: List<SchemaDefinition>`.
* Update your `GraphEdge` model. It must now include its own `id` (referencing the Entity table) so that it can be selected and edited like a node.

**Step 2.2: Redefine Schemas**

* Update `SchemaDefinitionItem` to separate "Properties" from "Roles".
* In the old code, roles were part of the JSON. Now, `RoleDefinition` should be its own distinct model class that maps directly to the `RoleDef` database table.
* Refactor `SchemaProperty` to include the `id` from the database. You need this ID to look up values later (since we aren't using string keys anymore).

---

### Phase 3: Repository & Data Access Layer

This is the heaviest coding phase. You need to rewrite the logic that talks to SQLite.

**Step 3.1: Implement "Create" Logic**

* **Creating Schemas:**
* Write a transaction that inserts the Schema row first to get an ID.
* Then, iterate through the properties and insert them into the `AttributeDef` table.
* If it's a Relation, iterate through roles and insert them into the `RoleDef` table.


* **Creating Entities (Nodes):**
* Insert a row into `Entity` to generate the ID.
* Insert row(s) into `EntityType` for every Schema the user selected (Composite support).
* Iterate through the user's input values. For each input, look up the `AttributeDefinition` to check the type ("INTEGER", "TEXT"), then insert into the correct column in `AttributeValue`.


* **Creating Relations (Edges):**
* Logic is almost identical to Entities (Insert Entity -> Insert EntityType -> Insert Attributes).
* **Additional Step:** Insert rows into `RelationLink` connecting this new Entity ID to the source/target Entity IDs using the specific Role IDs selected by the user.



**Step 3.2: Implement "Read" Logic (The Graph Loader)**

* **Fetch All Entities:** Query the `Entity` table.
* **Hydrate Types:** For each Entity, query `EntityType` to find out what it is.
* **Hydrate Attributes:** Query `AttributeValue` joining `AttributeDef`. Map the results so you have `Map<PropertyName, Value>` for the UI.
* **Hydrate Connections:** Query `RelationLink`.
* If an Entity has outgoing links in `RelationLink` (as the `RelationEntityID`), it is an Edge.
* If an Entity only appears as a `PlayerEntityID`, it is a Node.
* *Note:* In this system, an Edge is just a Node that holds relationships. You will need to filter your list of Entities into "GraphNodes" and "GraphEdges" for the UI engine.



---

### Phase 4: UI & ViewModel Updates

**Step 4.1: Schema Editor**

* Update the "Create Schema" screen to explicitly ask: "Is this a Node Type or a Relation Type?"
* If Relation, show the "Role Editor" (Source/Target definition).
* If Node, hide the Role Editor.

**Step 4.2: Node Editor (Composite Support)**

* Change the "Type" dropdown to a "Multi-Select" or "Tag" list.
* Allow the user to add "Person" *and* "Wizard".
* Dynamically render the form fields: Combine the property lists from both schemas. If "Person" asks for Name and "Wizard" asks for Mana, show both fields.

**Step 4.3: Graph Visualization**

* **Hyper-Edges:** Your existing rendering logic draws lines between X and Y.
* **Nested Relations (Edge-to-Edge):**
* Check if a "Relation" connects to another "Relation ID".
* If it does, you need to calculate the midpoint of the target Edge and draw the arrow pointing to that *line*, not a node circle.
* *Alternative Visual:* Render the target Edge as a "Diamond" node in the middle of the line, and have the new edge point to that Diamond. This is often easier for physics engines than pointing to a moving line segment.



**Step 4.4: Refactoring Fix (The Payoff)**

* Update the "Edit Schema" screen.
* Allow the user to rename a Property.
* The action simply updates the `AttributeDef` table `name` column.
* **Verify:** Open a Node that uses that schema. The input label should instantly update to the new name, with the value preserved, because the underlying link used IDs, not the string name.

End Implementation plan.

Prompt: This codebase is partially refactored. Your job is to finish refactoring according to the implementation plan
