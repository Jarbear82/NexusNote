package com.tau.nexusnote.codex.crud.update

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tau.nexusnote.codex.crud.create.CreateEdgeSchemaView
import com.tau.nexusnote.codex.crud.create.CreateEdgeView
import com.tau.nexusnote.codex.crud.create.CreateNodeSchemaView
import com.tau.nexusnote.codex.crud.create.CreateNodeView
import com.tau.nexusnote.datamodels.EditScreenState
import com.tau.nexusnote.datamodels.NodeDisplayItem
import com.tau.nexusnote.datamodels.NodeType
import com.tau.nexusnote.datamodels.RoleDefinition
import com.tau.nexusnote.datamodels.SchemaDefinitionItem
import com.tau.nexusnote.datamodels.SchemaProperty

@Composable
fun EditItemView(
    editScreenState: EditScreenState,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,

    // Node Creation Handlers
    onNodeCreationSchemaSelected: (SchemaDefinitionItem) -> Unit,
    onNodeCreationTypeSelected: (NodeType) -> Unit = {},
    onNodeCreationPropertyChanged: (String, String) -> Unit,
    onNodeCreationTextChanged: (String) -> Unit = {},
    onNodeCreationImageChanged: (String?, String) -> Unit = {_,_ ->},
    // New Creation Handlers
    onNodeCreationTableDataChange: (Int, Int, String) -> Unit = {_,_,_ ->},
    onNodeCreationTableHeaderChange: (Int, String) -> Unit = {_,_ ->},
    onNodeCreationAddTableRow: () -> Unit = {},
    onNodeCreationAddTableColumn: () -> Unit = {},
    onNodeCreationCodeDataChange: (String, String, String) -> Unit = {_,_,_ ->},
    onNodeCreationListItemChange: (Int, String) -> Unit = {_,_ ->},
    onNodeCreationAddListItem: () -> Unit = {},
    onNodeCreationRemoveListItem: (Int) -> Unit = {},
    onNodeCreationTaskItemChange: (Int, String, Boolean) -> Unit = {_,_,_ ->},
    onNodeCreationAddTaskItem: () -> Unit = {},
    onNodeCreationRemoveTaskItem: (Int) -> Unit = {},
    onNodeCreationAddTag: (String) -> Unit = {},
    onNodeCreationRemoveTag: (String) -> Unit = {},

    // Edge Creation Handlers
    onEdgeCreationSchemaSelected: (SchemaDefinitionItem) -> Unit,
    onEdgeCreationAddParticipant: (role: String) -> Unit,
    onEdgeCreationRemoveParticipant: (index: Int) -> Unit,
    onEdgeCreationParticipantSelected: (index: Int, node: NodeDisplayItem) -> Unit,
    onEdgeCreationPropertyChanged: (String, String) -> Unit,

    // Node Schema Creation Handlers
    onNodeSchemaTableNameChange: (String) -> Unit,
    onNodeSchemaTypeChange: (NodeType) -> Unit = {},
    onNodeSchemaTableConfigChange: (String, Boolean, String) -> Unit = {_,_,_ ->},
    onNodeSchemaCodeConfigChange: (String, Boolean) -> Unit = {_,_ ->},
    onNodeSchemaTextConfigChange: (String, Float, String) -> Unit = {_,_,_ ->},
    onNodeSchemaListConfigChange: (String) -> Unit = {},
    onNodeSchemaPropertyChange: (Int, SchemaProperty) -> Unit,
    onAddNodeSchemaProperty: (SchemaProperty) -> Unit,
    onRemoveNodeSchemaProperty: (Int) -> Unit,

    // Edge Schema Creation Handlers
    onEdgeSchemaTableNameChange: (String) -> Unit,
    onEdgeSchemaAddRole: (RoleDefinition) -> Unit,
    onEdgeSchemaRemoveRole: (Int) -> Unit,
    onEdgeSchemaRoleChange: (Int, RoleDefinition) -> Unit,
    onEdgeSchemaPropertyChange: (Int, SchemaProperty) -> Unit,
    onAddEdgeSchemaProperty: (SchemaProperty) -> Unit,
    onRemoveEdgeSchemaProperty: (Int) -> Unit,

    // Node Edit Handlers
    onNodeEditPropertyChange: (String, String) -> Unit,
    onNodeEditTextChanged: (String) -> Unit = {},
    onNodeEditImageChanged: (String?, String) -> Unit = {_,_ ->},
    // New Edit Handlers (Reuse signatures, route to edit VM logic)
    onNodeEditTableDataChange: (Int, Int, String) -> Unit = {_,_,_ ->},
    onNodeEditTableHeaderChange: (Int, String) -> Unit = {_,_ ->},
    onNodeEditAddTableRow: () -> Unit = {},
    onNodeEditAddTableColumn: () -> Unit = {},
    onNodeEditCodeDataChange: (String, String, String) -> Unit = {_,_,_ ->},
    onNodeEditListItemChange: (Int, String) -> Unit = {_,_ ->},
    onNodeEditAddListItem: () -> Unit = {},
    onNodeEditRemoveListItem: (Int) -> Unit = {},
    onNodeEditTaskItemChange: (Int, String, Boolean) -> Unit = {_,_,_ ->},
    onNodeEditAddTaskItem: () -> Unit = {},
    onNodeEditRemoveTaskItem: (Int) -> Unit = {},
    onNodeEditAddTag: (String) -> Unit = {},
    onNodeEditRemoveTag: (String) -> Unit = {},

    // Edge Edit Handlers
    onEdgeEditPropertyChange: (String, String) -> Unit,

    // Node Schema Edit Handlers
    onNodeSchemaEditLabelChange: (String) -> Unit,
    onNodeSchemaEditPropertyChange: (Int, SchemaProperty) -> Unit,
    onNodeSchemaEditAddProperty: (SchemaProperty) -> Unit,
    onNodeSchemaEditRemoveProperty: (Int) -> Unit,

    // Edge Schema Edit Handlers
    onEdgeSchemaEditLabelChange: (String) -> Unit,
    onEdgeSchemaEditPropertyChange: (Int, SchemaProperty) -> Unit,
    onEdgeSchemaEditAddProperty: (SchemaProperty) -> Unit,
    onEdgeSchemaEditRemoveProperty: (Int) -> Unit,
    onEdgeSchemaEditAddRole: (RoleDefinition) -> Unit,
    onEdgeSchemaEditRemoveRole: (Int) -> Unit,
    onEdgeSchemaEditRoleChange: (Int, RoleDefinition) -> Unit
) {
    when (editScreenState) {
        is EditScreenState.CreateNode -> {
            CreateNodeView(
                nodeCreationState = editScreenState.state,
                onSchemaSelected = onNodeCreationSchemaSelected,
                onNodeTypeSelected = onNodeCreationTypeSelected,
                onPropertyChanged = onNodeCreationPropertyChanged,
                onTextChanged = onNodeCreationTextChanged,
                onImageSelected = onNodeCreationImageChanged,
                onCreateClick = onSaveClick,
                onCancelClick = onCancelClick,
                // Pass new handlers
                onTableDataChange = onNodeCreationTableDataChange,
                onTableHeaderChange = onNodeCreationTableHeaderChange,
                onAddTableRow = onNodeCreationAddTableRow,
                onAddTableColumn = onNodeCreationAddTableColumn,
                onCodeDataChange = onNodeCreationCodeDataChange,
                onListItemChange = onNodeCreationListItemChange,
                onAddListItem = onNodeCreationAddListItem,
                onRemoveListItem = onNodeCreationRemoveListItem,
                onTaskItemChange = onNodeCreationTaskItemChange,
                onAddTaskItem = onNodeCreationAddTaskItem,
                onRemoveTaskItem = onNodeCreationRemoveTaskItem,
                onAddTag = onNodeCreationAddTag,
                onRemoveTag = onNodeCreationRemoveTag
            )
        }
        is EditScreenState.CreateEdge -> {
            CreateEdgeView(
                edgeCreationState = editScreenState.state,
                onSchemaSelected = onEdgeCreationSchemaSelected,
                onAddParticipant = onEdgeCreationAddParticipant,
                onRemoveParticipant = onEdgeCreationRemoveParticipant,
                onParticipantNodeSelected = onEdgeCreationParticipantSelected,
                onPropertyChanged = onEdgeCreationPropertyChanged,
                onCreateClick = onSaveClick,
                onCancelClick = onCancelClick
            )
        }
        is EditScreenState.CreateNodeSchema -> {
            CreateNodeSchemaView(
                state = editScreenState.state,
                onTableNameChange = onNodeSchemaTableNameChange,
                onTypeChange = onNodeSchemaTypeChange,
                onTableConfigChange = onNodeSchemaTableConfigChange,
                onCodeConfigChange = onNodeSchemaCodeConfigChange,
                onTextConfigChange = onNodeSchemaTextConfigChange,
                onListConfigChange = onNodeSchemaListConfigChange,
                onPropertyChange = onNodeSchemaPropertyChange,
                onAddProperty = onAddNodeSchemaProperty,
                onRemoveProperty = onRemoveNodeSchemaProperty,
                onCreate = { onSaveClick() },
                onCancel = onCancelClick
            )
        }
        is EditScreenState.CreateEdgeSchema -> {
            CreateEdgeSchemaView(
                state = editScreenState.state,
                onTableNameChange = onEdgeSchemaTableNameChange,
                onAddRole = onEdgeSchemaAddRole,
                onRemoveRole = onEdgeSchemaRemoveRole,
                onRoleChange = onEdgeSchemaRoleChange,
                onPropertyChange = onEdgeSchemaPropertyChange,
                onAddProperty = onAddEdgeSchemaProperty,
                onRemoveProperty = onRemoveEdgeSchemaProperty,
                onCreate = { onSaveClick() },
                onCancel = onCancelClick
            )
        }
        is EditScreenState.EditNode -> {
            EditNodeView(
                state = editScreenState.state,
                onPropertyChange = onNodeEditPropertyChange,
                onTextChanged = onNodeEditTextChanged,
                onImageChanged = onNodeEditImageChanged,
                onSave = onSaveClick,
                onCancel = onCancelClick,
                // Pass new handlers
                onTableDataChange = onNodeEditTableDataChange,
                onTableHeaderChange = onNodeEditTableHeaderChange,
                onAddTableRow = onNodeEditAddTableRow,
                onAddTableColumn = onNodeEditAddTableColumn,
                onCodeDataChange = onNodeEditCodeDataChange,
                onListItemChange = onNodeEditListItemChange,
                onAddListItem = onNodeEditAddListItem,
                onRemoveListItem = onNodeEditRemoveListItem,
                onTaskItemChange = onNodeEditTaskItemChange,
                onAddTaskItem = onNodeEditAddTaskItem,
                onRemoveTaskItem = onNodeEditRemoveTaskItem,
                onAddTag = onNodeEditAddTag,
                onRemoveTag = onNodeEditRemoveTag
            )
        }
        is EditScreenState.EditEdge -> {
            EditEdgeView(
                state = editScreenState.state,
                onPropertyChange = onEdgeEditPropertyChange,
                onSave = onSaveClick,
                onCancel = onCancelClick
            )
        }
        is EditScreenState.EditNodeSchema -> {
            EditNodeSchemaView(
                state = editScreenState.state,
                onLabelChange = onNodeSchemaEditLabelChange,
                onPropertyChange = onNodeSchemaEditPropertyChange,
                onAddProperty = onNodeSchemaEditAddProperty,
                onRemoveProperty = onNodeSchemaEditRemoveProperty,
                onSave = onSaveClick,
                onCancel = onCancelClick
            )
        }
        is EditScreenState.EditEdgeSchema -> {
            EditEdgeSchemaView(
                state = editScreenState.state,
                onLabelChange = onEdgeSchemaEditLabelChange,
                onPropertyChange = onEdgeSchemaEditPropertyChange,
                onAddProperty = onEdgeSchemaEditAddProperty,
                onRemoveProperty = onEdgeSchemaEditRemoveProperty,
                onAddRole = onEdgeSchemaEditAddRole,
                onRemoveRole = onEdgeSchemaEditRemoveRole,
                onRoleChange = onEdgeSchemaEditRoleChange,
                onSave = onSaveClick,
                onCancel = onCancelClick
            )
        }
        is EditScreenState.None -> {
            Text("No item selected to edit.")
        }
    }
}