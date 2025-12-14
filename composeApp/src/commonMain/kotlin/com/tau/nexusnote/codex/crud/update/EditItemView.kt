package com.tau.nexusnote.codex.crud.update

    import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import com.tau.nexusnote.codex.crud.create.CreateEdgeSchemaView
            import com.tau.nexusnote.codex.crud.create.CreateEdgeView
            import com.tau.nexusnote.codex.crud.create.CreateNodeSchemaView
            import com.tau.nexusnote.codex.crud.create.CreateNodeView
            import com.tau.nexusnote.datamodels.ConnectionPair
            import com.tau.nexusnote.datamodels.EditScreenState
            import com.tau.nexusnote.datamodels.NodeDisplayItem
            import com.tau.nexusnote.datamodels.SchemaDefinitionItem
            import com.tau.nexusnote.datamodels.SchemaProperty

            @Composable
            fun EditItemView(
                editScreenState: EditScreenState,
                onSaveClick: () -> Unit,
                onCancelClick: () -> Unit,

                // Node Creation Handlers
                onNodeCreationSchemaSelected: (SchemaDefinitionItem) -> Unit,
                onNodeCreationPropertyChanged: (String, String) -> Unit,

                // Edge Creation Handlers
                onEdgeCreationSchemaSelected: (SchemaDefinitionItem) -> Unit,
                onEdgeCreationConnectionSelected: (ConnectionPair) -> Unit,
                onEdgeCreationSrcSelected: (NodeDisplayItem) -> Unit,
                onEdgeCreationDstSelected: (NodeDisplayItem) -> Unit,
                onEdgeCreationPropertyChanged: (String, String) -> Unit,

                // Node Schema Creation Handlers
                onNodeSchemaTableNameChange: (String) -> Unit,
                onNodeSchemaPropertyChange: (Int, SchemaProperty) -> Unit,
                onAddNodeSchemaProperty: (SchemaProperty) -> Unit,
                onRemoveNodeSchemaProperty: (Int) -> Unit,

                // Edge Schema Creation Handlers
                onEdgeSchemaTableNameChange: (String) -> Unit,
                onEdgeSchemaCreationAddConnection: (String, String) -> Unit,
                onEdgeSchemaCreationRemoveConnection: (Int) -> Unit,
                onEdgeSchemaPropertyChange: (Int, SchemaProperty) -> Unit,
                onAddEdgeSchemaProperty: (SchemaProperty) -> Unit,
                onRemoveEdgeSchemaProperty: (Int) -> Unit,

                // Node Edit Handlers
                onNodeEditPropertyChange: (String, String) -> Unit,

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
                            onCancelClick = onCancelClick
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
