package com.tau.nexusnote.codex.crud.update

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tau.nexusnote.codex.crud.create.CreateEdgeSchemaView
import com.tau.nexusnote.codex.crud.create.CreateEdgeView
import com.tau.nexusnote.codex.crud.create.CreateNodeSchemaView
import com.tau.nexusnote.codex.crud.create.CreateNodeView
import com.tau.nexusnote.datamodels.EditScreenState
import com.tau.nexusnote.datamodels.NodeDisplayItem
import com.tau.nexusnote.datamodels.RoleDefinition
import com.tau.nexusnote.datamodels.SchemaDefinition
import com.tau.nexusnote.datamodels.SchemaProperty

@Composable
fun EditItemView(
    editScreenState: EditScreenState,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,

    // Node Creation Handlers
    onNodeCreationSchemaToggle: (SchemaDefinition) -> Unit,
    onNodeCreationPropertyChanged: (String, String) -> Unit,

    // Edge Creation Handlers
    onEdgeCreationSchemaSelected: (SchemaDefinition) -> Unit,
    onEdgeCreationAddParticipant: (role: String) -> Unit,
    onEdgeCreationRemoveParticipant: (index: Int) -> Unit,
    onEdgeCreationParticipantSelected: (index: Int, node: NodeDisplayItem) -> Unit,
    onEdgeCreationPropertyChanged: (String, String) -> Unit,

    // Node Schema Creation Handlers
    onNodeSchemaTableNameChange: (String) -> Unit,
    onNodeSchemaPropertyChange: (Int, SchemaProperty) -> Unit,
    onAddNodeSchemaProperty: (SchemaProperty) -> Unit,
    onRemoveNodeSchemaProperty: (Int) -> Unit,
    onNodeSchemaCanBePropertyTypeChange: (Boolean) -> Unit,

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
    onNodeEditSchemaToggle: (SchemaDefinition) -> Unit,

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
                onSchemaToggle = onNodeCreationSchemaToggle,
                onPropertyChanged = onNodeCreationPropertyChanged,
                onCreateClick = onSaveClick,
                onCancelClick = onCancelClick
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
                onPropertyChange = onNodeSchemaPropertyChange,
                onAddProperty = onAddNodeSchemaProperty,
                onRemoveProperty = onRemoveNodeSchemaProperty,
                onCanBePropertyTypeChange = onNodeSchemaCanBePropertyTypeChange,
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
                onSchemaToggle = onNodeEditSchemaToggle,
                onPropertyChange = onNodeEditPropertyChange,
                onSave = onSaveClick,
                onCancel = onCancelClick
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
                onCanBePropertyTypeChange = onNodeSchemaCanBePropertyTypeChange,
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