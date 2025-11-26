package com.tau.nexus_note.codex.crud.update

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tau.nexus_note.codex.crud.create.CreateEdgeSchemaView
import com.tau.nexus_note.codex.crud.create.CreateEdgeView
import com.tau.nexus_note.codex.crud.create.CreateNodeSchemaView
import com.tau.nexus_note.codex.crud.create.CreateNodeView
import com.tau.nexus_note.datamodels.ConnectionPair
import com.tau.nexus_note.datamodels.EditScreenState
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.SchemaDefinitionItem
import com.tau.nexus_note.datamodels.SchemaProperty

@Composable
fun EditItemView(
    editScreenState: EditScreenState,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    codexPath: String, // Pass path down

    // ... (All existing callbacks) ...
    onNodeCreationSchemaSelected: (SchemaDefinitionItem) -> Unit,
    onNodeCreationPropertyChanged: (String, String) -> Unit,
    onEdgeCreationSchemaSelected: (SchemaDefinitionItem) -> Unit,
    onEdgeCreationConnectionSelected: (ConnectionPair) -> Unit,
    onEdgeCreationSrcSelected: (NodeDisplayItem) -> Unit,
    onEdgeCreationDstSelected: (NodeDisplayItem) -> Unit,
    onEdgeCreationPropertyChanged: (String, String) -> Unit,
    onNodeSchemaTableNameChange: (String) -> Unit,
    onNodeSchemaPropertyChange: (Int, SchemaProperty) -> Unit,
    onAddNodeSchemaProperty: (SchemaProperty) -> Unit,
    onRemoveNodeSchemaProperty: (Int) -> Unit,
    onEdgeSchemaTableNameChange: (String) -> Unit,
    onEdgeSchemaCreationAddConnection: (String, String) -> Unit,
    onEdgeSchemaCreationRemoveConnection: (Int) -> Unit,
    onEdgeSchemaPropertyChange: (Int, SchemaProperty) -> Unit,
    onAddEdgeSchemaProperty: (SchemaProperty) -> Unit,
    onRemoveEdgeSchemaProperty: (Int) -> Unit,
    onNodeEditPropertyChange: (String, String) -> Unit,
    onEdgeEditPropertyChange: (String, String) -> Unit,
    onNodeSchemaEditLabelChange: (String) -> Unit,
    onNodeSchemaEditPropertyChange: (Int, SchemaProperty) -> Unit,
    onNodeSchemaEditAddProperty: (SchemaProperty) -> Unit,
    onNodeSchemaEditRemoveProperty: (Int) -> Unit,
    onEdgeSchemaEditLabelChange: (String) -> Unit,
    onEdgeSchemaEditPropertyChange: (Int, SchemaProperty) -> Unit,
    onEdgeSchemaEditAddProperty: (SchemaProperty) -> Unit,
    onEdgeSchemaEditRemoveProperty: (Int) -> Unit,
    onEdgeSchemaEditAddConnection: (src: String, dst: String) -> Unit,
    onEdgeSchemaEditRemoveConnection: (Int) -> Unit
) {
    when (editScreenState) {
        is EditScreenState.CreateNode -> {
            CreateNodeView(
                nodeCreationState = editScreenState.state,
                onSchemaSelected = onNodeCreationSchemaSelected,
                onPropertyChanged = onNodeCreationPropertyChanged,
                onCreateClick = onSaveClick,
                onCancelClick = onCancelClick,
                codexPath = codexPath
            )
        }
        is EditScreenState.CreateEdge -> {
            CreateEdgeView(
                edgeCreationState = editScreenState.state,
                onSchemaSelected = onEdgeCreationSchemaSelected,
                onConnectionSelected = onEdgeCreationConnectionSelected,
                onSrcSelected = onEdgeCreationSrcSelected,
                onDstSelected = onEdgeCreationDstSelected,
                onPropertyChanged = onEdgeCreationPropertyChanged,
                onCreateClick = onSaveClick,
                onCancelClick = onCancelClick,
                codexPath = codexPath
            )
        }
        is EditScreenState.CreateNodeSchema -> {
            CreateNodeSchemaView(
                state = editScreenState.state,
                onTableNameChange = onNodeSchemaTableNameChange,
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
                onAddConnection = onEdgeSchemaCreationAddConnection,
                onRemoveConnection = onEdgeSchemaCreationRemoveConnection,
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
                onSave = onSaveClick,
                onCancel = onCancelClick,
                codexPath = codexPath
            )
        }
        is EditScreenState.EditEdge -> {
            EditEdgeView(
                state = editScreenState.state,
                onPropertyChange = onEdgeEditPropertyChange,
                onSave = onSaveClick,
                onCancel = onCancelClick,
                codexPath = codexPath // Need to update EditEdgeView too, though not shown in previous list, logic is identical
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
                onSave = onSaveClick,
                onCancel = onCancelClick,
                onAddConnection = onEdgeSchemaEditAddConnection,
                onRemoveConnection = onEdgeSchemaEditRemoveConnection,
            )
        }
        is EditScreenState.None -> {
            Text("No item selected to edit.")
        }
    }
}